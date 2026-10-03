package com.weekend.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/** A chat thread. */
public record Conversation(String id, String title, Instant createdAt, boolean archived) {

    public Conversation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        title = title == null || title.isBlank() ? "New conversation" : title;
    }
}
