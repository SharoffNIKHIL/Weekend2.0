package com.weekend.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A one-time notification (recurrence comes later; see DESIGN.md §9.3).
 * {@code folderId} is optional: null means "not in a folder".
 */
public record Reminder(String id, String text, Instant dueAt, ReminderStatus status, Instant createdAt, String folderId) {

    public Reminder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Reminder(String id, String text, Instant dueAt, ReminderStatus status, Instant createdAt) {
        this(id, text, dueAt, status, createdAt, null);
    }

    public Reminder withStatus(ReminderStatus next) {
        return new Reminder(id, text, dueAt, next, createdAt, folderId);
    }

    public Reminder inFolder(String folder) {
        return new Reminder(id, text, dueAt, status, createdAt, folder);
    }
}
