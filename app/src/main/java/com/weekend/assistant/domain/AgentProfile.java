package com.weekend.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * An agent the owner can chat with or delegate to.
 *
 * @param instructions       CUSTOM only: the agent's own instructions, appended after Weekend's fixed safety rules
 * @param instructionsSource where the instructions came from, for display (e.g. "file: CLAUDE.md", "written in app")
 * @param endpoint           REMOTE only: base URL of the other agent (must be on the allow-list)
 * @param inboundTokenHash   REMOTE only: SHA-256 of the token the other agent uses to message Weekend; never exported
 */
public record AgentProfile(
        String id,
        String name,
        String description,
        AgentKind kind,
        String instructions,
        String instructionsSource,
        AgentConditions conditions,
        String endpoint,
        String inboundTokenHash,
        boolean editable,
        Instant createdAt) {

    public AgentProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public AgentProfile withConditions(AgentConditions next) {
        return new AgentProfile(id, name, description, kind, instructions, instructionsSource, next, endpoint,
                inboundTokenHash, editable, createdAt);
    }
}
