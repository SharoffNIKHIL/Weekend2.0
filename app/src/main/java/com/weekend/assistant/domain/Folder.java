package com.weekend.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/** A home-screen folder that groups tasks and reminders. {@code icon} is a key from the UI icon set. */
public record Folder(String id, String name, String icon, Instant createdAt) {

    public Folder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Folder renamed(String newName, String newIcon) {
        return new Folder(id, newName, newIcon, createdAt);
    }
}
