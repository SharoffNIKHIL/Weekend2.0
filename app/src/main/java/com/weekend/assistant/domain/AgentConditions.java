package com.weekend.assistant.domain;

import java.util.List;

/**
 * Owner-set conditions for an agent.
 *
 * @param allowedTools   tools the agent may use; null = every registered tool, empty = none
 * @param confirmAllTools every tool call (even read-only ones) waits for the owner's yes
 * @param thinkHarder    always use the stronger model
 * @param maxToolSteps   per-turn tool-step limit (capped by the global limit)
 */
public record AgentConditions(List<String> allowedTools, boolean confirmAllTools, boolean thinkHarder, int maxToolSteps) {

    public static final AgentConditions DEFAULT = new AgentConditions(null, false, false, 5);

    public AgentConditions {
        allowedTools = allowedTools == null ? null : List.copyOf(allowedTools);
        maxToolSteps = Math.max(0, maxToolSteps);
    }

    public boolean allows(String tool) {
        return allowedTools == null || allowedTools.contains(tool);
    }
}
