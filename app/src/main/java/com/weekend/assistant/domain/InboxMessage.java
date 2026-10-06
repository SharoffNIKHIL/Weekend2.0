package com.weekend.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/** A message from a connected agent (or the system). Its body is DATA, never instructions. */
public record InboxMessage(String id, String fromAgentId, String fromName, String subject, String body, Instant createdAt, boolean read) {

    public InboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromAgentId, "fromAgentId");
        Objects.requireNonNull(fromName, "fromName");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public InboxMessage markedRead() {
        return new InboxMessage(id, fromAgentId, fromName, subject, body, createdAt, true);
    }
}
