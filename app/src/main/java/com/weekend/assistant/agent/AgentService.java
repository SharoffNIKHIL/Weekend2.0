package com.weekend.assistant.agent;

import com.weekend.assistant.agents.AgentDirectory;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.AgentConditions;
import com.weekend.assistant.domain.AgentKind;
import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.domain.Conversation;
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
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * The agent loop (DESIGN §7.2): build context → pick a model → call the LLM → run allowed tools →
 * repeat (max N steps) → save the turn, memories, tool log and audit entries. Write-tools pause
 * and return a {@link PendingAction} for the owner to confirm. Each chat runs as one agent (AgentDirectory):
 * its instructions are added after the fixed safety rules, and its conditions narrow the tools, force
 * confirmation or pick the stronger model. Chats with a REMOTE agent always pause for the owner's yes (🔓).
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
    private final AgentDirectory agents;
    private final NotificationService notifications;
    private final WeekendProperties props;
    private final Clock clock;
    private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();

    public AgentService(LlmProvider llm, ToolRegistry tools, ModelRouter router, ContextBuilder context,
            CostCalculator costs, MemoryService memories, ConversationRepository conversations,
            MessageRepository messages, ToolCallRepository toolCalls, SecretFilter secrets, AuditLog audit,
            AgentDirectory agents, NotificationService notifications, WeekendProperties props, Clock clock) {
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
        this.agents = agents;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
    }

    public ChatResult chat(String conversationIdOrNull, String userText, boolean thinkHarder) {
        return chat(conversationIdOrNull, userText, thinkHarder, null);
    }

    public ChatResult chat(String conversationIdOrNull, String userText, boolean thinkHarder, String agentIdOrNull) {
        AgentProfile agent = agents.find(agentIdOrNull).orElseThrow(() -> new IllegalArgumentException("unknown agent"));
        AgentConditions cond = agent.conditions();
        enforceDailyCap();
        Instant now = clock.instant();
        String safeText = secrets.redact(userText); // P4: secrets never reach the LLM, storage or titles
        Conversation conv = Optional.ofNullable(conversationIdOrNull)
                .flatMap(conversations::findById)
                .orElseGet(() -> conversations.save(new Conversation(UUID.randomUUID().toString(), title(safeText), now, false)));

        List<Message> history = messages.recent(conv.id(), props.agent().maxHistoryTurns());
        Message userMsg = messages.save(new Message(UUID.randomUUID().toString(), conv.id(), Role.USER,
                safeText, null, 0, 0, BigDecimal.ZERO, now));
        boolean memorySaved = memories.extractExplicit(userText, userMsg.id()).isPresent();
        if (agent.kind() == AgentKind.REMOTE) {
            return toRemote(agent, conv, safeText, memorySaved);
        }

        List<Memory> relevant = memories.search(safeText, props.agent().maxMemoriesInContext());
        String model = router.choose(safeText, thinkHarder || cond.thinkHarder());
        String system = context.systemPrompt(relevant, agent);
        List<ToolSpec> specs = tools.specs().stream().filter(t -> cond.allows(t.name())).toList();
        int maxSteps = agent.kind() == AgentKind.BUILTIN ? props.agent().maxToolSteps()
                : Math.min(props.agent().maxToolSteps(), cond.maxToolSteps());
        List<Turn> turns = new ArrayList<>(context.turns(history, safeText));

        List<String> used = new ArrayList<>();
        int tokensIn = 0;
        int tokensOut = 0;
        String reply = "";
        PendingAction awaiting = null;

        for (int step = 0; step <= maxSteps; step++) {
            LlmResponse res = llm.complete(new LlmRequest(model, system, List.copyOf(turns), specs,
                    props.llm().maxOutputTokens()));
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
                if (!cond.allows(use.name())) {
                    results.add(new ToolResult(use.id(), "Tool not allowed for this agent: " + use.name(), true));
                    continue;
                }
                if (tool.get().writes() || cond.confirmAllTools()) {
                    awaiting = hold(conv.id(), use.name(), use.input(),
                            "Run " + use.name() + " with " + secrets.redact(String.valueOf(use.input())) + "?");
                    results.add(new ToolResult(use.id(), "Waiting for the owner's confirmation; do not repeat this call.", false));
                    continue;
                }
                ToolOutput out = runTool(tool.get(), use.input(), new ToolContext(conv.id(), userMsg.id()), false);
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
        return new ChatResult(conv.id(), reply, model, used, awaiting, cost, memorySaved, agent.id(), agent.name());
    }

    /** A chat addressed to a remote agent: nothing is sent until the owner approves (🔓 data exit). */
    private ChatResult toRemote(AgentProfile agent, Conversation conv, String safeText, boolean memorySaved) {
        String host = URI.create(agent.endpoint()).getHost();
        PendingAction awaiting = hold(conv.id(), "agent_delegate", Map.of("agent_id", agent.id(), "message", safeText),
                "Send your message to " + agent.name() + " (" + host + ")? It leaves Weekend.");
        String reply = "This message goes to " + agent.name() + ", outside Weekend. Approve it to send.";
        messages.save(new Message(UUID.randomUUID().toString(), conv.id(), Role.ASSISTANT, reply, "remote:" + agent.id(),
                0, 0, BigDecimal.ZERO, clock.instant()));
        audit.append("agent", "chat.remote_hold", conv.id());
        return new ChatResult(conv.id(), reply, "remote:" + agent.name(), List.of(), awaiting, BigDecimal.ZERO, memorySaved,
                agent.id(), agent.name());
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
        if (action == null) {
            return Optional.empty();
        }
        if (!approved) {
            audit.append("owner", "tool.declined", action.tool());
            return Optional.of("Cancelled. Nothing was changed.");
        }
        ToolOutput out = tools.find(action.tool())
                .map(t -> runTool(t, action.input(), new ToolContext(action.conversationId(), null), true))
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
