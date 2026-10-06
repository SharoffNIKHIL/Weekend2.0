package com.weekend.assistant.inbox;

import com.weekend.assistant.domain.Notification;
import com.weekend.assistant.domain.NotificationKind;
import com.weekend.assistant.port.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** In-app notifications (P5: kept for weekend.retention.notifications). Push delivery comes with Phase 2. */
@Service
public class NotificationService {

    private static final int MAX_TEXT = 300;

    private final NotificationRepository repo;
    private final Clock clock;

    public NotificationService(NotificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public Notification notify(NotificationKind kind, String title, String body, String link) {
        return repo.save(new Notification(UUID.randomUUID().toString(), kind, clip(title), clip(body), link, clock.instant(), false));
    }

    public List<Notification> all() {
        return repo.findAll();
    }

    public long unreadCount() {
        return repo.findAll().stream().filter(n -> !n.read()).count();
    }

    public Optional<Notification> markRead(String id) {
        return repo.findById(id).map(n -> repo.save(n.markedRead()));
    }

    public int markAllRead() {
        List<Notification> unread = repo.findAll().stream().filter(n -> !n.read()).toList();
        unread.forEach(n -> repo.save(n.markedRead()));
        return unread.size();
    }

    public boolean delete(String id) {
        return repo.deleteById(id);
    }

    /** P5 cleanup; returns how many were removed. */
    public int deleteOlderThan(Instant cutoff) {
        List<Notification> old = repo.findAll().stream().filter(n -> n.createdAt().isBefore(cutoff)).toList();
        old.forEach(n -> repo.deleteById(n.id()));
        return old.size();
    }

    private static String clip(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.length() <= MAX_TEXT ? t : t.substring(0, MAX_TEXT - 1) + "…";
    }
}
