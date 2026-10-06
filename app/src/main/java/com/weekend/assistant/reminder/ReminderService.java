package com.weekend.assistant.reminder;

import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.domain.NotificationKind;
import com.weekend.assistant.domain.ReminderStatus;
import com.weekend.assistant.inbox.NotificationService;
import com.weekend.assistant.port.FolderRepository;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.ReminderRepository;
import com.weekend.assistant.port.ReminderScheduler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Creates, lists, cancels and delivers one-time reminders. No polling: each is scheduled once. */
@Service
public class ReminderService {

    private final ReminderRepository repo;
    private final ReminderScheduler scheduler;
    private final AuditLog audit;
    private final FolderRepository folders;
    private final NotificationService notifications;
    private final Clock clock;

    public ReminderService(ReminderRepository repo, ReminderScheduler scheduler, AuditLog audit, FolderRepository folders,
            NotificationService notifications, Clock clock) {
        this.repo = repo;
        this.scheduler = scheduler;
        this.audit = audit;
        this.folders = folders;
        this.notifications = notifications;
        this.clock = clock;
    }

    public Optional<Reminder> create(String text, Instant dueAt) {
        return create(text, dueAt, null);
    }

    /** Creates a reminder, optionally in a folder. Empty when the time is not in the future. */
    public Optional<Reminder> create(String text, Instant dueAt, String folderId) {
        Instant now = clock.instant();
        if (text == null || text.isBlank() || text.strip().length() > 500) {
            throw new IllegalArgumentException("text is required (max 500 characters)");
        }
        String folder = checkedFolder(folderId);
        if (dueAt == null || !dueAt.isAfter(now)) {
            return Optional.empty();
        }
        Reminder r = repo.save(new Reminder(UUID.randomUUID().toString(), text.strip(), dueAt, ReminderStatus.SCHEDULED, now, folder));
        scheduler.schedule(r);
        audit.append("owner", "reminder.create", r.id());
        return Optional.of(r);
    }

    public List<Reminder> all() {
        return repo.findAll();
    }

    public long upcomingCount() {
        return repo.findAll().stream().filter(r -> r.status() == ReminderStatus.SCHEDULED).count();
    }

    public Optional<Reminder> move(String id, String folderId) {
        String folder = checkedFolder(folderId);
        return repo.findById(id).map(r -> {
            audit.append("owner", "reminder.move", id);
            return repo.save(r.inFolder(folder));
        });
    }

    public Optional<Reminder> cancel(String id) {
        return repo.findById(id).filter(r -> r.status() == ReminderStatus.SCHEDULED).map(r -> {
            scheduler.cancel(id);
            audit.append("owner", "reminder.cancel", id);
            return repo.save(r.withStatus(ReminderStatus.CANCELLED));
        });
    }

    /** Called by the worker when the scheduled task fires. Idempotent. */
    public Optional<Reminder> markDelivered(String id) {
        return repo.findById(id).filter(r -> r.status() == ReminderStatus.SCHEDULED).map(r -> {
            audit.append("system", "reminder.deliver", id);
            notifications.notify(NotificationKind.REMINDER, "Reminder", r.text(), "#reminders");
            return repo.save(r.withStatus(ReminderStatus.DELIVERED));
        });
    }

    private String checkedFolder(String folderId) {
        if (folderId == null || folderId.isBlank()) {
            return null;
        }
        if (folders.findById(folderId).isEmpty()) {
            throw new IllegalArgumentException("unknown folder");
        }
        return folderId;
    }
}
