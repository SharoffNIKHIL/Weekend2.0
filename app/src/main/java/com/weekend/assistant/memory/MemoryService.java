package com.weekend.assistant.memory;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.MemoryKind;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.MemoryRepository;
import com.weekend.assistant.security.SecretFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Saves, searches, pins and deletes memories (P5 retention, P6 owner control, P4 no secrets). */
@Service
public class MemoryService {

    private static final Pattern REMEMBER = Pattern.compile("(?i)^\\s*(please\\s+)?remember\\s+(that\\s+)?(.{3,500})$");
    private static final int MAX_LENGTH = 500;

    private final MemoryRepository repo;
    private final SecretFilter secrets;
    private final AuditLog audit;
    private final WeekendProperties props;
    private final Clock clock;

    public MemoryService(MemoryRepository repo, SecretFilter secrets, AuditLog audit, WeekendProperties props, Clock clock) {
        this.repo = repo;
        this.secrets = secrets;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    /** Saves a memory unless it looks like a secret; unpinned memories expire per retention policy. */
    public Optional<Memory> remember(String text, MemoryKind kind, String sourceMessageId) {
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty() || clean.length() > MAX_LENGTH || secrets.containsSecret(clean)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Memory m = new Memory(UUID.randomUUID().toString(), clean, kind, sourceMessageId, now, now,
                now.plus(props.retention().unpinnedMemories()), false);
        repo.save(m);
        audit.append("agent", "memory.create", m.id());
        return Optional.of(m);
    }

    /** Heuristic extractor for explicit "remember that …" messages. The Phase 3 LLM extractor replaces it. */
    public Optional<Memory> extractExplicit(String userText, String sourceMessageId) {
        Matcher m = REMEMBER.matcher(userText == null ? "" : userText);
        return m.matches() ? remember(m.group(3), MemoryKind.FACT, sourceMessageId) : Optional.empty();
    }

    /** Search and mark results as used (extends nothing; pinned memories never expire). */
    public List<Memory> search(String query, int limit) {
        Instant now = clock.instant();
        return repo.search(query, limit).stream().map(m -> repo.save(m.touchedAt(now))).toList();
    }

    public List<Memory> all() {
        return repo.findAll();
    }

    public Optional<Memory> pin(String id, boolean pinned) {
        return repo.findById(id).map(m -> {
            Memory updated = repo.save(m.withPinned(pinned));
            audit.append("owner", pinned ? "memory.pin" : "memory.unpin", id);
            return updated;
        });
    }

    public boolean delete(String id) {
        boolean removed = repo.delete(id);
        if (removed) {
            audit.append("owner", "memory.delete", id);
        }
        return removed;
    }
}
