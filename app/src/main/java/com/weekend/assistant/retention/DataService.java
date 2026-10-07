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
import java.util.Map;
import org.springframework.stereotype.Service;
import com.weekend.assistant.features.FeatureCatalog;
import com.weekend.assistant.domain.Folder;
import com.weekend.assistant.domain.InboxMessage;
import com.weekend.assistant.domain.Notification;
import com.weekend.assistant.domain.Payment;
import com.weekend.assistant.domain.Task;
import com.weekend.assistant.port.FolderRepository;
import com.weekend.assistant.port.InboxMessageRepository;
import com.weekend.assistant.port.NotificationRepository;
import com.weekend.assistant.port.PaymentRepository;
import com.weekend.assistant.port.TaskRepository;
import org.springframework.stereotype.Component;

/** P6: export everything, or permanently delete everything (audit log is kept, per P8). */
@Service
public class DataService {

    public static final String DELETE_CONFIRMATION = "DELETE ALL MY DATA";

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MemoryRepository memories;
    private final ReminderRepository reminders;
    private final ToolCallRepository toolCalls;
    private final Workspace workspace;
    private final FeatureCatalog features;
    private final AuditLog audit;
    private final Clock clock;
    private List<OwnerData> extra = List.of();

    public DataService(ConversationRepository conversations, MessageRepository messages, MemoryRepository memories,
            ReminderRepository reminders, ToolCallRepository toolCalls, Workspace workspace, FeatureCatalog features,
            AuditLog audit, Clock clock) {
        this.workspace = workspace;
        this.features = features;
        this.conversations = conversations;
        this.messages = messages;
        this.memories = memories;
        this.reminders = reminders;
        this.toolCalls = toolCalls;
        this.audit = audit;
        this.clock = clock;
    }

    /** Components with their own owner data (Studio videos …), included in export and delete-all. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setOwnerData(List<OwnerData> extra) {
        this.extra = List.copyOf(extra);
    }

    public Export export(String actor) {
        audit.append(actor, "data.export", "all");
        return new Export(clock.instant(), conversations.findAll(), messages.findAll(), memories.findAll(),
                reminders.findAll(), toolCalls.findAll(), workspace.folders().findAll(), workspace.tasks().findAll(),
                workspace.payments().findAll(), workspace.inbox().findAll(), workspace.notifications().findAll(), features.overrides(),
                audit.findAll(), extras());
    }

    private Map<String, Object> extras() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        extra.forEach(o -> out.put(o.name(), o.export()));
        return out;
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
        workspace.folders().deleteAll();
        workspace.tasks().deleteAll();
        workspace.payments().deleteAll();
        workspace.inbox().deleteAll();
        workspace.notifications().deleteAll();
        features.resetAll();
        extra.forEach(OwnerData::deleteAll);
        audit.append("owner", "data.delete_all", "all");
        return true;
    }

    /** The workspace repositories, grouped to keep the constructor readable. */
    @Component
    public record Workspace(FolderRepository folders, TaskRepository tasks, PaymentRepository payments,
            InboxMessageRepository inbox, NotificationRepository notifications) {}

    public record Export(Instant exportedAt, List<Conversation> conversations, List<Message> messages,
            List<Memory> memories, List<Reminder> reminders, List<ToolCallRecord> toolCalls, List<Folder> folders,
            List<Task> tasks, List<Payment> payments, List<InboxMessage> inboxMessages, List<Notification> notifications,
            Map<String, Map<String, Object>> featureSettings, List<AuditEntry> auditLog, Map<String, Object> other) {}
}
