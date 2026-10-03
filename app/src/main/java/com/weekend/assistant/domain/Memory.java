package com.weekend.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A remembered fact. Never holds secrets (filtered by {@code SecretFilter} before saving).
 * {@code expiresAt} is null for pinned memories (kept until the owner deletes them).
 */
public record Memory(
        String id,
        String text,
        MemoryKind kind,
        String sourceMessageId,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean pinned) {

    public Memory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Memory withPinned(boolean value) {
        return new Memory(id, text, kind, sourceMessageId, createdAt, lastUsedAt, value ? null : expiresAt, value);
    }

    public Memory touchedAt(Instant when) {
        return new Memory(id, text, kind, sourceMessageId, createdAt, when, expiresAt, pinned);
    }
}
