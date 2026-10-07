package com.weekend.assistant.retention;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.inbox.InboxService;
import com.weekend.assistant.inbox.NotificationService;
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
    private final NotificationService notifications;
    private final InboxService inbox;
    private final AuditLog audit;
    private final WeekendProperties props;
    private final Clock clock;
    private java.util.List<OwnerData> extra = java.util.List.of();

    public RetentionService(MessageRepository messages, MemoryRepository memories, ToolCallRepository toolCalls,
            NotificationService notifications, InboxService inbox, AuditLog audit, WeekendProperties props, Clock clock) {
        this.notifications = notifications;
        this.inbox = inbox;
        this.messages = messages;
        this.memories = memories;
        this.toolCalls = toolCalls;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setOwnerData(java.util.List<OwnerData> extra) {
        this.extra = java.util.List.copyOf(extra);
    }

    public RetentionReport run() {
        Instant now = clock.instant();
        int msgs = messages.deleteOlderThan(now.minus(props.retention().messages()));
        int mems = memories.deleteExpired(now);
        int calls = toolCalls.deleteOlderThan(now.minus(props.retention().toolCalls()));
        int notes = notifications.deleteOlderThan(now.minus(props.retention().notifications()));
        int inboxMsgs = inbox.deleteOlderThan(now.minus(props.retention().inboxMessages()));
        java.util.Map<String, Integer> other = new java.util.LinkedHashMap<>();
        extra.forEach(o -> other.put(o.name(), o.purgeExpired(now)));
        audit.append("system", "retention.run", "messages=" + msgs + ",memories=" + mems + ",toolCalls=" + calls
                + ",notifications=" + notes + ",inboxMessages=" + inboxMsgs + (other.isEmpty() ? "" : "," + other));
        return new RetentionReport(msgs, mems, calls, notes, inboxMsgs, other);
    }

    public record RetentionReport(int messagesDeleted, int memoriesDeleted, int toolCallsDeleted,
            int notificationsDeleted, int inboxMessagesDeleted, java.util.Map<String, Integer> otherDeleted) {}
}
