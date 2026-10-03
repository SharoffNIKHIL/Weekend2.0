package com.weekend.assistant.domain;

import java.time.Instant;

/**
 * Append-only security log entry (P8). Each entry stores the hash of the previous one, so any
 * edit or deletion breaks the chain and is detected by {@code AuditLog#verify()}.
 */
public record AuditEntry(long seq, Instant ts, String actor, String action, String target, String hashPrev, String hashSelf) {}
