package com.weekend.assistant;

import com.weekend.assistant.adapter.memory.InMemoryAuditLog;
import com.weekend.assistant.adapter.memory.InMemoryConversationRepository;
import com.weekend.assistant.adapter.memory.InMemoryMemoryRepository;
import com.weekend.assistant.adapter.memory.InMemoryMessageRepository;
import com.weekend.assistant.adapter.memory.InMemoryReminderRepository;
import com.weekend.assistant.adapter.memory.InMemoryToolCallRepository;
import com.weekend.assistant.adapter.memory.LoggingReminderScheduler;
import com.weekend.assistant.agent.AgentService;
import com.weekend.assistant.agent.ContextBuilder;
import com.weekend.assistant.agent.CostCalculator;
import com.weekend.assistant.agent.ModelRouter;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.memory.MemoryService;
import com.weekend.assistant.port.LlmProvider;
import com.weekend.assistant.reminder.ReminderService;
import com.weekend.assistant.retention.RetentionService;
import com.weekend.assistant.security.SecretFilter;
import com.weekend.assistant.tools.CurrentTimeTool;
import com.weekend.assistant.tools.MemorySaveTool;
import com.weekend.assistant.tools.MemorySearchTool;
import com.weekend.assistant.tools.ReminderCreateTool;
import com.weekend.assistant.tools.ToolRegistry;
import java.time.Instant;
import java.util.List;

/** Wires the real services with in-memory adapters, a test clock and any LLM. */
public final class Harness {

    public final TestFixtures.MutableClock clock = new TestFixtures.MutableClock(Instant.parse("2026-10-03T04:30:00Z"));
    public final WeekendProperties props;
    public final InMemoryConversationRepository conversations = new InMemoryConversationRepository();
    public final InMemoryMessageRepository messages = new InMemoryMessageRepository();
    public final InMemoryMemoryRepository memoryRepo = new InMemoryMemoryRepository();
    public final InMemoryReminderRepository reminderRepo = new InMemoryReminderRepository();
    public final InMemoryToolCallRepository toolCalls = new InMemoryToolCallRepository();
    public final LoggingReminderScheduler scheduler = new LoggingReminderScheduler();
    public final InMemoryAuditLog audit = new InMemoryAuditLog(clock);
    public final SecretFilter secrets = new SecretFilter();
    public final MemoryService memories;
    public final ReminderService reminders;
    public final RetentionService retention;
    public final AgentService agent;

    public Harness(LlmProvider llm) {
        this(llm, TestFixtures.props());
    }

    public Harness(LlmProvider llm, WeekendProperties props) {
        this.props = props;
        memories = new MemoryService(memoryRepo, secrets, audit, props, clock);
        reminders = new ReminderService(reminderRepo, scheduler, audit, clock);
        retention = new RetentionService(messages, memoryRepo, toolCalls, audit, props, clock);
        ToolRegistry registry = new ToolRegistry(List.of(new CurrentTimeTool(clock, props), new MemorySearchTool(memories),
                new MemorySaveTool(memories), new ReminderCreateTool(reminders, props)));
        agent = new AgentService(llm, registry, new ModelRouter(props), new ContextBuilder(props), new CostCalculator(props),
                memories, conversations, messages, toolCalls, secrets, audit, props, clock);
    }
}
