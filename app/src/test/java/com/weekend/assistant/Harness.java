package com.weekend.assistant;

import com.weekend.assistant.adapter.memory.InMemoryAgentProfileRepository;
import com.weekend.assistant.adapter.memory.InMemoryAuditLog;
import com.weekend.assistant.adapter.memory.InMemoryConversationRepository;
import com.weekend.assistant.adapter.memory.InMemoryFolderRepository;
import com.weekend.assistant.adapter.memory.InMemoryInboxMessageRepository;
import com.weekend.assistant.adapter.memory.InMemoryMemoryRepository;
import com.weekend.assistant.adapter.memory.InMemoryMessageRepository;
import com.weekend.assistant.adapter.memory.InMemoryNotificationRepository;
import com.weekend.assistant.adapter.memory.InMemoryPaymentRepository;
import com.weekend.assistant.adapter.memory.InMemoryReminderRepository;
import com.weekend.assistant.adapter.memory.InMemoryTaskRepository;
import com.weekend.assistant.adapter.memory.InMemoryToolCallRepository;
import com.weekend.assistant.adapter.memory.LoggingReminderScheduler;
import com.weekend.assistant.agent.AgentService;
import com.weekend.assistant.agent.ContextBuilder;
import com.weekend.assistant.agent.CostCalculator;
import com.weekend.assistant.agent.ModelRouter;
import com.weekend.assistant.agents.AgentDirectory;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.inbox.InboxService;
import com.weekend.assistant.inbox.NotificationService;
import com.weekend.assistant.memory.MemoryService;
import com.weekend.assistant.port.LlmProvider;
import com.weekend.assistant.port.RemoteAgentClient;
import com.weekend.assistant.reminder.ReminderService;
import com.weekend.assistant.retention.DataService;
import com.weekend.assistant.retention.RetentionService;
import com.weekend.assistant.security.SecretFilter;
import com.weekend.assistant.tools.AgentDelegateTool;
import com.weekend.assistant.tools.CurrentTimeTool;
import com.weekend.assistant.tools.MathEvaluateTool;
import com.weekend.assistant.tools.MathSolveTool;
import com.weekend.assistant.tools.MathStatsTool;
import com.weekend.assistant.tools.MemorySaveTool;
import com.weekend.assistant.tools.MemorySearchTool;
import com.weekend.assistant.tools.ReminderCreateTool;
import com.weekend.assistant.tools.TaskCreateTool;
import com.weekend.assistant.tools.TaskListTool;
import com.weekend.assistant.tools.ToolRegistry;
import com.weekend.assistant.tools.WebSearchTool;
import com.weekend.assistant.port.WebSearchClient;
import com.weekend.assistant.workspace.ApprovalService;
import com.weekend.assistant.workspace.FolderService;
import com.weekend.assistant.workspace.HomeService;
import com.weekend.assistant.workspace.PaymentService;
import com.weekend.assistant.workspace.TaskService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Wires the real services with in-memory adapters, a test clock, any LLM and a fake remote-agent client. */
public final class Harness {

    public final TestFixtures.MutableClock clock = new TestFixtures.MutableClock(Instant.parse("2026-10-03T04:30:00Z"));
    public final WeekendProperties props;
    public final InMemoryConversationRepository conversations = new InMemoryConversationRepository();
    public final InMemoryMessageRepository messages = new InMemoryMessageRepository();
    public final InMemoryMemoryRepository memoryRepo = new InMemoryMemoryRepository();
    public final InMemoryReminderRepository reminderRepo = new InMemoryReminderRepository();
    public final InMemoryToolCallRepository toolCalls = new InMemoryToolCallRepository();
    public final InMemoryFolderRepository folderRepo = new InMemoryFolderRepository();
    public final InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();
    public final InMemoryPaymentRepository paymentRepo = new InMemoryPaymentRepository();
    public final InMemoryInboxMessageRepository inboxRepo = new InMemoryInboxMessageRepository();
    public final InMemoryNotificationRepository notificationRepo = new InMemoryNotificationRepository();
    public final InMemoryAgentProfileRepository agentRepo = new InMemoryAgentProfileRepository();
    public final LoggingReminderScheduler scheduler = new LoggingReminderScheduler();
    public final InMemoryAuditLog audit = new InMemoryAuditLog(clock);
    public final SecretFilter secrets = new SecretFilter();
    public final FakeRemoteAgent remote = new FakeRemoteAgent();
    public final FakeWeb web = new FakeWeb();
    public final NotificationService notifications;
    public final InboxService inbox;
    public final MemoryService memories;
    public final ReminderService reminders;
    public final FolderService folders;
    public final TaskService tasks;
    public final PaymentService payments;
    public final AgentDirectory agents;
    public final RetentionService retention;
    public final AgentService agent;
    public final ApprovalService approvals;
    public final HomeService home;
    public final DataService data;

    public Harness(LlmProvider llm) {
        this(llm, TestFixtures.props());
    }

    public Harness(LlmProvider llm, WeekendProperties props) {
        this.props = props;
        notifications = new NotificationService(notificationRepo, clock);
        inbox = new InboxService(inboxRepo, notifications, secrets, audit, clock);
        memories = new MemoryService(memoryRepo, secrets, audit, props, clock);
        reminders = new ReminderService(reminderRepo, scheduler, audit, folderRepo, notifications, clock);
        folders = new FolderService(folderRepo, taskRepo, reminderRepo, audit, clock);
        tasks = new TaskService(taskRepo, folderRepo, secrets, audit, clock);
        payments = new PaymentService(paymentRepo, notifications, secrets, audit, clock);
        agents = new AgentDirectory(agentRepo, secrets, audit, props, clock);
        retention = new RetentionService(messages, memoryRepo, toolCalls, notifications, inbox, audit, props, clock);
        ToolRegistry registry = new ToolRegistry(List.of(new CurrentTimeTool(clock, props), new MemorySearchTool(memories),
                new MemorySaveTool(memories), new ReminderCreateTool(reminders, props), new TaskCreateTool(tasks, props),
                new TaskListTool(tasks), new AgentDelegateTool(agents, remote, inbox, secrets), new MathEvaluateTool(),
                new MathSolveTool(), new MathStatsTool(), new WebSearchTool(web, secrets)));
        agent = new AgentService(llm, registry, new ModelRouter(props), new ContextBuilder(props), new CostCalculator(props),
                memories, conversations, messages, toolCalls, secrets, audit, agents, notifications, props, clock);
        approvals = new ApprovalService(agent, payments);
        home = new HomeService(reminders, tasks, approvals, payments, inbox, notifications, folders);
        data = new DataService(conversations, messages, memoryRepo, reminderRepo, toolCalls,
                new DataService.Workspace(folderRepo, taskRepo, paymentRepo, inboxRepo, notificationRepo), agents, audit, clock);
    }

    /** Records what was sent; replies with a canned answer or fails on demand. */
    public static final class FakeRemoteAgent implements RemoteAgentClient {
        public final List<String> sent = new ArrayList<>();
        public String reply = "remote reply";
        public boolean fail;
        public boolean healthy = true;

        @Override
        public String send(AgentProfile agent, String message, String conversationId) {
            if (fail) {
                throw new RemoteAgentException("could not reach the agent (test)");
            }
            sent.add(message);
            return reply;
        }

        @Override
        public boolean healthy(AgentProfile agent) {
            return healthy;
        }
    }

    /** Web search stand-in: off unless {@code enabled}; records queries. */
    public static final class FakeWeb implements WebSearchClient {
        public final List<String> queries = new ArrayList<>();
        public boolean enabled;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public List<Result> search(String query, int limit) {
            queries.add(query + "|" + limit);
            return List.of(new Result("Compound interest", "https://example.org/wiki/Compound_interest", "A = P(1 + r/n)^(nt)"));
        }

        @Override
        public String summary(String title) {
            return "Summary of " + title;
        }
    }
}
