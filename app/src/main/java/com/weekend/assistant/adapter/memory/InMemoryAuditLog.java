package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.AuditEntry;
import com.weekend.assistant.port.AuditLog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Hash-chained audit log: hashSelf = SHA-256(seq | ts | actor | action | target | hashPrev). */
@Repository
public class InMemoryAuditLog implements AuditLog {

    static final String GENESIS = "0".repeat(64);

    private final List<AuditEntry> entries = new ArrayList<>();
    private final Clock clock;

    public InMemoryAuditLog(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized AuditEntry append(String actor, String action, String target) {
        long seq = entries.size() + 1L;
        String prev = entries.isEmpty() ? GENESIS : entries.get(entries.size() - 1).hashSelf();
        Instant ts = clock.instant();
        AuditEntry entry = new AuditEntry(seq, ts, actor, action, target, prev, hash(seq, ts, actor, action, target, prev));
        entries.add(entry);
        return entry;
    }

    @Override
    public synchronized List<AuditEntry> findAll() {
        return List.copyOf(entries);
    }

    @Override
    public synchronized boolean verify() {
        String prev = GENESIS;
        for (AuditEntry e : entries) {
            if (!e.hashPrev().equals(prev)
                    || !e.hashSelf().equals(hash(e.seq(), e.ts(), e.actor(), e.action(), e.target(), e.hashPrev()))) {
                return false;
            }
            prev = e.hashSelf();
        }
        return true;
    }

    /** Test hook: simulates tampering with stored data. Package-private on purpose. */
    synchronized void replaceForTest(int index, AuditEntry forged) {
        entries.set(index, forged);
    }

    static String hash(long seq, Instant ts, String actor, String action, String target, String prev) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String material = String.join("|", Long.toString(seq), ts.toString(), actor, action, target, prev);
            return HexFormat.of().formatHex(sha.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
