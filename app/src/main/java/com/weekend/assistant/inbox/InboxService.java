package com.weekend.assistant.inbox;

import com.weekend.assistant.domain.InboxMessage;
import com.weekend.assistant.domain.NotificationKind;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.InboxMessageRepository;
import com.weekend.assistant.security.SecretFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Messages from connected agents (and the system). Bodies are redacted for secrets (P4), size-limited, and are
 * always DATA: nothing in a message is executed or fed to the model as an instruction.
 */
@Service
public class InboxService {

    public static final int MAX_SUBJECT = 200;
    public static final int MAX_BODY = 8000;

    private final InboxMessageRepository repo;
    private final NotificationService notifications;
    private final SecretFilter secrets;
    private final AuditLog audit;
    private final Clock clock;

    public InboxService(InboxMessageRepository repo, NotificationService notifications, SecretFilter secrets, AuditLog audit, Clock clock) {
        this.repo = repo;
        this.notifications = notifications;
        this.secrets = secrets;
        this.audit = audit;
        this.clock = clock;
    }

    public InboxMessage receive(String fromAgentId, String fromName, String subject, String body) {
        String s = clip(secrets.redact(subject == null || subject.isBlank() ? "(no subject)" : subject), MAX_SUBJECT);
        String b = clip(secrets.redact(body == null ? "" : body), MAX_BODY);
        InboxMessage m = repo.save(new InboxMessage(UUID.randomUUID().toString(), fromAgentId, fromName, s, b, clock.instant(), false));
        audit.append("agent:" + fromAgentId, "inbox.receive", m.id());
        notifications.notify(NotificationKind.MESSAGE, "New message from " + fromName, s, "#messages");
        return m;
    }

    public List<InboxMessage> all() {
        return repo.findAll();
    }

    public long unreadCount() {
        return repo.findAll().stream().filter(m -> !m.read()).count();
    }

    public Optional<InboxMessage> markRead(String id) {
        return repo.findById(id).map(m -> repo.save(m.markedRead()));
    }

    public boolean delete(String id) {
        boolean removed = repo.deleteById(id);
        if (removed) {
            audit.append("owner", "inbox.delete", id);
        }
        return removed;
    }

    public int deleteOlderThan(Instant cutoff) {
        List<InboxMessage> old = repo.findAll().stream().filter(m -> m.createdAt().isBefore(cutoff)).toList();
        old.forEach(m -> repo.deleteById(m.id()));
        return old.size();
    }

    private static String clip(String s, int max) {
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
