package com.weekend.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * An agent the owner can chat with or delegate to.
 *
 * @param instructions       CUSTOM only: the agent's own instructions, appended after Weekend's fixed safety rules
 * @param instructionsSource where the instructions came from, for display (e.g. "file: CLAUDE.md", "written in app")
 * @param persona            humor, truth, focus, efficiency, mode, search and approval ranges
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
        AgentPersona persona,
        String endpoint,
        String inboundTokenHash,
        boolean editable,
        Instant createdAt) {

    public AgentProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(conditions, "conditions");
        persona = persona == null ? AgentPersona.DEFAULT : persona;
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public AgentProfile withConditions(AgentConditions next) {
        return new AgentProfile(id, name, description, kind, instructions, instructionsSource, next, persona, endpoint,
                inboundTokenHash, editable, createdAt);
    }

    public AgentProfile withPersona(AgentPersona next) {
        return new AgentProfile(id, name, description, kind, instructions, instructionsSource, conditions, next, endpoint,
                inboundTokenHash, editable, createdAt);
    }
}
