package com.weekend.assistant.workspace;

import com.weekend.assistant.domain.PaymentStatus;
import com.weekend.assistant.domain.ReminderStatus;
import com.weekend.assistant.domain.TaskStatus;
import com.weekend.assistant.inbox.InboxService;
import com.weekend.assistant.inbox.NotificationService;
import com.weekend.assistant.reminder.ReminderService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Everything the home screen shows in one call: category counts, folders and what's up next. */
@Service
public class HomeService {

    private final ReminderService reminders;
    private final TaskService tasks;
    private final ApprovalService approvals;
    private final PaymentService payments;
    private final InboxService inbox;
    private final NotificationService notifications;
    private final FolderService folders;

    public HomeService(ReminderService reminders, TaskService tasks, ApprovalService approvals, PaymentService payments,
            InboxService inbox, NotificationService notifications, FolderService folders) {
        this.reminders = reminders;
        this.tasks = tasks;
        this.approvals = approvals;
        this.payments = payments;
        this.inbox = inbox;
        this.notifications = notifications;
        this.folders = folders;
    }

    public record Counts(long reminders, long tasks, long approvals, long payments, long messages, long notifications) {}

    /** One upcoming item; {@code type} is "task" or "reminder". */
    public record UpNext(String id, String type, String title, Instant dueAt, String folderId) {}

    public record Home(Counts counts, List<FolderService.FolderSummary> folders, List<UpNext> upNext) {}

    public Home home() {
        long unpaid = payments.all().stream()
                .filter(p -> p.status() == PaymentStatus.PENDING_APPROVAL || p.status() == PaymentStatus.APPROVED).count();
        Counts counts = new Counts(reminders.upcomingCount(), tasks.openCount(), approvals.pending().size(), unpaid,
                inbox.unreadCount(), notifications.unreadCount());
        Stream<UpNext> r = reminders.all().stream().filter(x -> x.status() == ReminderStatus.SCHEDULED)
                .map(x -> new UpNext(x.id(), "reminder", x.text(), x.dueAt(), x.folderId()));
        Stream<UpNext> t = tasks.all().stream().filter(x -> x.status() == TaskStatus.OPEN && x.dueAt() != null)
                .map(x -> new UpNext(x.id(), "task", x.title(), x.dueAt(), x.folderId()));
        List<UpNext> next = Stream.concat(r, t).sorted(Comparator.comparing(UpNext::dueAt)).limit(5).toList();
        return new Home(counts, folders.summaries(), next);
    }
}
