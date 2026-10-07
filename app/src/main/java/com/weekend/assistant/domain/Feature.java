package com.weekend.assistant.domain;

import java.util.List;
import java.util.Objects;

/**
 * What the owner wants Weekend to be right now (Optimal, Research, Coding, Drawing, Notes …). A feature sets the
 * guidelines added to the system prompt, the default persona, and which capabilities (plugins and connectors) are
 * attached. There is one agent; features only change how it behaves.
 */
public record Feature(
        String id,
        String name,
        FeatureGroup group,
        String icon,
        String tagline,
        String description,
        List<String> guidelines,
        List<String> capabilities,
        AgentPersona persona,
        boolean acceptsImages,
        boolean makesArt) {

    public Feature {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(group, "group");
        guidelines = List.copyOf(guidelines);
        capabilities = List.copyOf(capabilities);
        Objects.requireNonNull(persona, "persona");
    }
}
