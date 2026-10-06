package com.weekend.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/** A to-do item. {@code dueAt} and {@code folderId} are optional. */
public record Task(
        String id,
        String title,
        String notes,
        String folderId,
        Instant dueAt,
        TaskPriority priority,
        TaskStatus status,
        Instant createdAt,
        Instant completedAt) {

    public Task {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Task completed(Instant when) {
        return new Task(id, title, notes, folderId, dueAt, priority, TaskStatus.DONE, createdAt, when);
    }

    public Task reopened() {
        return new Task(id, title, notes, folderId, dueAt, priority, TaskStatus.OPEN, createdAt, null);
    }

    public Task inFolder(String folder) {
        return new Task(id, title, notes, folder, dueAt, priority, status, createdAt, completedAt);
    }
}
