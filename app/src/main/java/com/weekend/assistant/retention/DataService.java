package com.weekend.assistant.retention;

import com.weekend.assistant.domain.AuditEntry;
import com.weekend.assistant.domain.Conversation;
import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.Message;
import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.domain.ToolCallRecord;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.ConversationRepository;
import com.weekend.assistant.port.MemoryRepository;
import com.weekend.assistant.port.MessageRepository;
import com.weekend.assistant.port.ReminderRepository;
import com.weekend.assistant.port.ToolCallRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** P6: export everything, or permanently delete everything (audit log is kept, per P8). */
@Service
public class DataService {

    public static final String DELETE_CONFIRMATION = "DELETE ALL MY DATA";

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MemoryRepository memories;
    private final ReminderRepository reminders;
    private final ToolCallRepository toolCalls;
    private final AuditLog audit;
    private final Clock clock;

    public DataService(ConversationRepository conversations, MessageRepository messages, MemoryRepository memories,
            ReminderRepository reminders, ToolCallRepository toolCalls, AuditLog audit, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.memories = memories;
        this.reminders = reminders;
        this.toolCalls = toolCalls;
        this.audit = audit;
        this.clock = clock;
    }

    public Export export(String actor) {
        audit.append(actor, "data.export", "all");
        return new Export(clock.instant(), conversations.findAll(), messages.findAll(), memories.findAll(),
                reminders.findAll(), toolCalls.findAll(), audit.findAll());
    }

    /** Deletes all owner data when the exact confirmation phrase is given. Returns false otherwise. */
    public boolean deleteAll(String confirmation) {
        if (!DELETE_CONFIRMATION.equals(confirmation)) {
            return false;
        }
        conversations.deleteAll();
        messages.deleteAll();
        memories.deleteAll();
        reminders.deleteAll();
        toolCalls.deleteAll();
        audit.append("owner", "data.delete_all", "all");
        return true;
    }

    public record Export(Instant exportedAt, List<Conversation> conversations, List<Message> messages,
            List<Memory> memories, List<Reminder> reminders, List<ToolCallRecord> toolCalls, List<AuditEntry> auditLog) {}
}
