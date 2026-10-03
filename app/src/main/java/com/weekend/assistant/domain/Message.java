package com.weekend.assistant.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** One turn in a conversation, with token and cost accounting. */
public record Message(
        String id,
        String conversationId,
        Role role,
        String content,
        String model,
        int tokensIn,
        int tokensOut,
        BigDecimal costUsd,
        Instant createdAt) {

    public Message {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(createdAt, "createdAt");
        costUsd = costUsd == null ? BigDecimal.ZERO : costUsd;
    }
}
