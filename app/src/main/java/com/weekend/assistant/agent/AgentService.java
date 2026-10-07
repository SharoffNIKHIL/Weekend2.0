package com.weekend.assistant.agent;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.AgentPersona;
import com.weekend.assistant.domain.ApprovalRange;
import com.weekend.assistant.domain.SearchRange;
import com.weekend.assistant.domain.Conversation;
import com.weekend.assistant.features.FeatureCatalog;
import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.Message;
import com.weekend.assistant.domain.NotificationKind;
import com.weekend.assistant.domain.Role;
import com.weekend.assistant.domain.ToolCallRecord;
import com.weekend.assistant.inbox.NotificationService;
import com.weekend.assistant.memory.MemoryService;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.ConversationRepository;
import com.weekend.assistant.port.LlmProvider;
import com.weekend.assistant.port.LlmProvider.AssistantTurn;
import com.weekend.assistant.port.LlmProvider.ImagePart;
import com.weekend.assistant.port.LlmProvider.UserText;
import com.weekend.assistant.port.LlmProvider.LlmRequest;
import com.weekend.assistant.port.LlmProvider.LlmResponse;
import com.weekend.assistant.port.LlmProvider.ToolResult;
import com.weekend.assistant.port.LlmProvider.ToolResults;
import com.weekend.assistant.port.LlmProvider.ToolSpec;
import com.weekend.assistant.port.LlmProvider.ToolUse;
import com.weekend.assistant.port.LlmProvider.Turn;
import com.weekend.assistant.port.MessageRepository;
import com.weekend.assistant.port.ToolCallRepository;
import com.weekend.assistant.security.SecretFilter;
import com.weekend.assistant.tools.Tool;
import com.weekend.assistant.tools.ToolContext;
import com.weekend.assistant.tools.ToolOutput;
import com.weekend.assistant.tools.ToolRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * The agent loop (DESIGN §7.2): build context → pick a model → call the LLM → run allowed tools → repeat → save the
 * turn, memories, tool log and audit entries. There is one agent; the chosen feature (Optimal, Research, Drawing …)
 * adds its guidelines, persona and attached plugins/connectors. Write-tools, and anything outside the approval range,
 * pause and return a {@link PendingAction} for the owner to confirm. Images ride along on the user turn only.
 */
@Service
public class AgentService {

    private final LlmProvider llm;
    private final ToolRegistry tools;
    private final ModelRouter router;
    private final ContextBuilder context;
    private final CostCalculator costs;
    private final MemoryService memories;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ToolCallRepository toolCalls;
    private final SecretFilter secrets;
    private final AuditLog audit;
    private final FeatureCatalog features;
    private final NotificationService notifications;
    private final WeekendProperties props;
    private final Clock clock;
    private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();
    private final Map<String, SearchRange> pendingRange = new ConcurrentHashMap<>();
    private com.weekend.assistant.studio.StudioService studio;

    public AgentService(LlmProvider llm, ToolRegistry tools, ModelRouter router, ContextBuilder context,
            CostCalculator costs, MemoryService memories, ConversationRepository conversations,
            MessageRepository messages, ToolCallRepository toolCalls, SecretFilter secrets, AuditLog audit,
            FeatureCatalog features, NotificationService notifications, WeekendProperties props, Clock clock) {
        this.llm = llm;
        this.tools = tools;
        this.router = router;
        this.context = context;
        this.costs = costs;
        this.memories = memories;
        this.conversations = conversations;
        this.messages = messages;
        this.toolCalls = toolCalls;
        this.secrets = secrets;
        this.audit = audit;
        this.features = features;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    /** Weekend Studio takes over turns that ask for a video (or continue one being set up). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setStudio(com.weekend.assistant.studio.StudioService studio) {
        this.studio = studio;
    }

    public ChatResult chat(String conversationIdOrNull, String userText, boolean thinkHarder) {
        return chat(conversationIdOrNull, userText, thinkHarder, null, List.of());
    }

    public ChatResult chat(String conversationIdOrNull, String userText, boolean thinkHarder, String featureIdOrNull,
            List<ImagePart> images) {
        FeatureCatalog.Effective fx = features.find(featureIdOrNull).orElseThrow(() -> new IllegalArgumentException("unknown feature"));
        List<ImagePart> pictures = images == null ? List.of() : images;
        Set<String> attached = new HashSet<>();
        fx.feature().capabilities().forEach(c -> attached.addAll(com.weekend.assistant.features.Capability.valueOf(c).tools()));
        enforceDailyCap();
        Instant now = clock.instant();
        String safeText = secrets.redact(userText); // P4: secrets never reach the LLM, storage or titles
        Conversation conv = Optional.ofNullable(conversationIdOrNull)
                .flatMap(conversations::findById)
                .orElseGet(() -> conversations.save(new Conversation(UUID.randomUUID().toString(), title(safeText), now, false)));

        AgentPersona persona = fx.persona();
        AgentPersona.Budget budget = persona.budget();
        List<Message> history = messages.recent(conv.id(), budget.historyTurns());
        String stored = pictures.isEmpty() ? safeText : safeText + "\n[" + pictures.size() + " image" + (pictures.size() == 1 ? "" : "s")
                + " attached; images are not stored]";
        Message userMsg = messages.save(new Message(UUID.randomUUID().toString(), conv.id(), Role.USER,
                stored, null, 0, 0, BigDecimal.ZERO, now));
        boolean memorySaved = memories.extractExplicit(userText, userMsg.id()).isPresent();

        if (studio != null && pictures.isEmpty()) {
            Optional<com.weekend.assistant.studio.StudioReply> sr = studio.respond(conv.id(), safeText, "studio".equals(fx.feature().id()));
            if (sr.isPresent()) {
                messages.save(new Message(UUID.randomUUID().toString(), conv.id(), Role.ASSISTANT, sr.get().text(), "studio",
                        0, 0, BigDecimal.ZERO, clock.instant()));
                audit.append("agent", "studio.turn", conv.id());
                return new ChatResult(conv.id(), sr.get().text(), "studio", List.of(), null, BigDecimal.ZERO, memorySaved,
                        fx.feature().id(), fx.feature().name(), sr.get());
            }
        }

        List<Memory> relevant = context(safeText, budget.memories());
        String model = router.choose(safeText, thinkHarder || budget.strongModel());
        String system = context.systemPrompt(relevant, fx);
        List<ToolSpec> specs = tools.specs().stream()
                .filter(t -> attached.contains(t.name()) && inRange(persona.search(), t.name()) && tools.find(t.name()).map(Tool::available).orElse(false))
                .toList();
        int maxSteps = Math.min(props.agent().maxToolSteps(), budget.maxToolSteps());
        int maxTokens = Math.min(props.llm().maxOutputTokens(), budget.maxTokens());
        List<Turn> turns = new ArrayList<>(context.turns(history, safeText));
        if (!pictures.isEmpty()) {
            turns.set(turns.size() - 1, new UserText(safeText, pictures));
        }

        List<String> used = new ArrayList<>();
        int tokensIn = 0;
        int tokensOut = 0;
        String reply = "";
        PendingAction awaiting = null;

        for (int step = 0; step <= maxSteps; step++) {
            LlmResponse res = llm.complete(new LlmRequest(model, system, List.copyOf(turns), specs, maxTokens, persona.temperature()));
            tokensIn += res.inputTokens();
            tokensOut += res.outputTokens();
            reply = res.text() == null ? "" : secrets.redact(res.text());
            if (!res.wantsTools()) {
                break;
            }
            if (step == maxSteps) {
                reply = reply.isBlank() ? "I stopped after the maximum number of tool steps." : reply;
                break;
            }
            turns.add(new AssistantTurn(reply, res.toolUses()));
            List<ToolResult> results = new ArrayList<>();
            for (ToolUse use : res.toolUses()) {
                Optional<Tool> tool = tools.find(use.name());
                if (tool.isEmpty()) {
                    results.add(new ToolResult(use.id(), "Unknown tool: " + use.name(), true));
                    continue;
                }
                if (!tool.get().available()) {
                    results.add(new ToolResult(use.id(), "Tool not available right now: " + use.name(), true));
                    continue;
                }
                if (!attached.contains(use.name())) {
                    results.add(new ToolResult(use.id(), "Tool not attached to the " + fx.feature().name() + " feature: " + use.name(), true));
                    continue;
                }
                if (!inRange(persona.search(), use.name())) {
                    results.add(new ToolResult(use.id(), "Tool outside the search range set for " + fx.feature().name() + ": " + use.name(), true));
                    continue;
                }
                if (needsApproval(tool.get(), persona.approval())) {
                    awaiting = hold(conv.id(), use.name(), use.input(),
                            "Run " + use.name() + " with " + secrets.redact(String.valueOf(use.input())) + "?");
                    pendingRange.put(awaiting.id(), persona.search());
                    results.add(new ToolResult(use.id(), "Waiting for the owner's confirmation; do not repeat this call.", false));
                    continue;
                }
                ToolOutput out = runTool(tool.get(), use.input(), new ToolContext(conv.id(), userMsg.id(), persona.search()), false);
                used.add(use.name());
                results.add(new ToolResult(use.id(), ContextBuilder.asData(out.content()), out.isError()));
            }
            turns.add(new ToolResults(results));
            if (awaiting != null) {
                reply = reply.isBlank() ? awaiting.summary() : reply;
                break;
            }
        }

        BigDecimal cost = costs.cost(model, tokensIn, tokensOut);
        messages.save(new Message(UUID.randomUUID().toString(), conv.id(), Role.ASSISTANT, reply, model,
                tokensIn, tokensOut, cost, clock.instant()));
        audit.append("agent", "chat.turn", conv.id());
        return new ChatResult(conv.id(), reply, model, used, awaiting, cost, memorySaved, fx.feature().id(), fx.feature().name());
    }

    /** Pinned memories (your profile) always come first, then the ones relevant to this message. */
    private List<Memory> context(String text, int limit) {
        Map<String, Memory> out = new LinkedHashMap<>();
        memories.all().stream().filter(Memory::pinned).limit(Math.max(4, limit)).forEach(m -> out.put(m.id(), m));
        memories.search(text, limit).forEach(m -> out.putIfAbsent(m.id(), m));
        return List.copyOf(out.values());
    }

    /** Which tools a feature's search range lets it see: OFF = none that look things up; MEMORY = local only. */
    static boolean inRange(SearchRange range, String tool) {
        boolean lookup = tool.equals("memory_search") || tool.equals("task_list");
        boolean web = tool.equals("web_search");
        return switch (range) {
            case OFF -> !lookup && !web;
            case MEMORY -> !web;
            case WEB, WIDE -> true;
        };
    }

    /** Writes always ask; external tools ask unless the range is WRITES_ONLY; ALL asks for everything. */
    static boolean needsApproval(Tool tool, ApprovalRange range) {
        if (tool.writes() || range == ApprovalRange.ALL) {
            return true;
        }
        return tool.external() && range != ApprovalRange.WRITES_ONLY;
    }

    private PendingAction hold(String conversationId, String tool, Map<String, Object> input, String summary) {
        PendingAction action = new PendingAction(UUID.randomUUID().toString(), conversationId, tool, input, summary, clock.instant());
        pending.put(action.id(), action);
        notifications.notify(NotificationKind.APPROVAL, "Approval needed", summary, "#approvals");
        return action;
    }

    /** Owner answers yes/no to a pending write-tool call. */
    public Optional<String> confirm(String pendingId, boolean approved) {
        PendingAction action = pending.remove(pendingId);
        SearchRange range = pendingRange.remove(pendingId == null ? "" : pendingId);
        if (action == null) {
            return Optional.empty();
        }
        if (!approved) {
            audit.append("owner", "tool.declined", action.tool());
            return Optional.of("Cancelled. Nothing was changed.");
        }
        ToolOutput out = tools.find(action.tool())
                .map(t -> runTool(t, action.input(), new ToolContext(action.conversationId(), null, range), true))
                .orElse(ToolOutput.error("Tool no longer available"));
        return Optional.of(out.content());
    }

    public List<PendingAction> pendingActions() {
        return List.copyOf(pending.values());
    }

    private ToolOutput runTool(Tool tool, Map<String, Object> input, ToolContext ctx, boolean confirmed) {
        ToolOutput out;
        try {
            out = tool.execute(input, ctx);
        } catch (IllegalArgumentException e) {
            out = ToolOutput.error("Invalid input: " + e.getMessage());
        }
        toolCalls.save(new ToolCallRecord(UUID.randomUUID().toString(), ctx.messageId(), tool.name(),
                secrets.redact(String.valueOf(input)), out.content().length(), confirmed, clock.instant()));
        audit.append(confirmed ? "owner" : "agent", "tool." + tool.name(), ctx.conversationId());
        return out;
    }

    private void enforceDailyCap() {
        BigDecimal cap = props.agent().dailyCostCapUsd();
        if (cap == null || cap.signum() <= 0) {
            return;
        }
        Instant startOfDay = LocalDate.now(clock.withZone(props.zone())).atStartOfDay(props.zone()).toInstant();
        if (messages.costSince(startOfDay).compareTo(cap) >= 0) {
            throw new CostCapExceededException("Daily LLM cost cap of USD " + cap + " reached; try again tomorrow or raise the cap.");
        }
    }

    private static String title(String text) {
        String t = text.strip().replaceAll("\\s+", " ");
        return t.length() <= 60 ? t : t.substring(0, 57) + "...";
    }
}
