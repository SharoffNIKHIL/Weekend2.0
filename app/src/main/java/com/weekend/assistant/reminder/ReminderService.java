package com.weekend.assistant.reminder;

import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.domain.ReminderStatus;
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
    private final Clock clock;

    public ReminderService(ReminderRepository repo, ReminderScheduler scheduler, AuditLog audit, Clock clock) {
        this.repo = repo;
        this.scheduler = scheduler;
        this.audit = audit;
        this.clock = clock;
    }

    public Optional<Reminder> create(String text, Instant dueAt) {
        Instant now = clock.instant();
        if (!dueAt.isAfter(now)) {
            return Optional.empty();
        }
        Reminder r = repo.save(new Reminder(UUID.randomUUID().toString(), text.strip(), dueAt, ReminderStatus.SCHEDULED, now));
        scheduler.schedule(r);
        audit.append("owner", "reminder.create", r.id());
        return Optional.of(r);
    }

    public List<Reminder> all() {
        return repo.findAll();
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
            return repo.save(r.withStatus(ReminderStatus.DELIVERED));
        });
    }
}
