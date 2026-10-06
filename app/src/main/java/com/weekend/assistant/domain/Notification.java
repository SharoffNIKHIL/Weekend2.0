package com.weekend.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/** An in-app notification. {@code link} is a UI route such as {@code #approvals}. */
public record Notification(String id, NotificationKind kind, String title, String body, String link, Instant createdAt, boolean read) {

    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Notification markedRead() {
        return new Notification(id, kind, title, body, link, createdAt, true);
    }
}
