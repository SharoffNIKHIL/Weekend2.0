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
import com.weekend.assistant.agents.AgentDirectory;
import com.weekend.assistant.domain.AgentConditions;
import com.weekend.assistant.domain.AgentKind;
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
    private final AgentDirectory agents;
    private final AuditLog audit;
    private final Clock clock;

    public DataService(ConversationRepository conversations, MessageRepository messages, MemoryRepository memories,
            ReminderRepository reminders, ToolCallRepository toolCalls, Workspace workspace, AgentDirectory agents,
            AuditLog audit, Clock clock) {
        this.workspace = workspace;
        this.agents = agents;
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
        List<AgentExport> agentList = agents.all().stream()
                .map(a -> new AgentExport(a.id(), a.name(), a.kind(), a.instructions(), a.instructionsSource(), a.conditions(), a.endpoint()))
                .toList();
        return new Export(clock.instant(), conversations.findAll(), messages.findAll(), memories.findAll(),
                reminders.findAll(), toolCalls.findAll(), workspace.folders().findAll(), workspace.tasks().findAll(),
                workspace.payments().findAll(), workspace.inbox().findAll(), workspace.notifications().findAll(), agentList,
                audit.findAll());
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
        agents.deleteOwnerCreated();
        audit.append("owner", "data.delete_all", "all");
        return true;
    }

    /** The workspace repositories, grouped to keep the constructor readable. */
    @Component
    public record Workspace(FolderRepository folders, TaskRepository tasks, PaymentRepository payments,
            InboxMessageRepository inbox, NotificationRepository notifications) {}

    /** Agents in the export (P6: everything the owner wrote), minus inbound-token hashes. */
    public record AgentExport(String id, String name, AgentKind kind, String instructions, String instructionsSource,
            AgentConditions conditions, String endpoint) {}

    public record Export(Instant exportedAt, List<Conversation> conversations, List<Message> messages,
            List<Memory> memories, List<Reminder> reminders, List<ToolCallRecord> toolCalls, List<Folder> folders,
            List<Task> tasks, List<Payment> payments, List<InboxMessage> inboxMessages, List<Notification> notifications,
            List<AgentExport> agents, List<AuditEntry> auditLog) {}
}
