package com.weekend.assistant.retention;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.MemoryRepository;
import com.weekend.assistant.port.MessageRepository;
import com.weekend.assistant.port.ToolCallRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Nightly P5 cleanup (Cloud Scheduler → worker → POST /jobs/retention at 03:00 IST). */
@Service
public class RetentionService {

    private final MessageRepository messages;
    private final MemoryRepository memories;
    private final ToolCallRepository toolCalls;
    private final AuditLog audit;
    private final WeekendProperties props;
    private final Clock clock;

    public RetentionService(MessageRepository messages, MemoryRepository memories, ToolCallRepository toolCalls,
            AuditLog audit, WeekendProperties props, Clock clock) {
        this.messages = messages;
        this.memories = memories;
        this.toolCalls = toolCalls;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    public RetentionReport run() {
        Instant now = clock.instant();
        int msgs = messages.deleteOlderThan(now.minus(props.retention().messages()));
        int mems = memories.deleteExpired(now);
        int calls = toolCalls.deleteOlderThan(now.minus(props.retention().toolCalls()));
        audit.append("system", "retention.run", "messages=" + msgs + ",memories=" + mems + ",toolCalls=" + calls);
        return new RetentionReport(msgs, mems, calls);
    }

    public record RetentionReport(int messagesDeleted, int memoriesDeleted, int toolCallsDeleted) {}
}
