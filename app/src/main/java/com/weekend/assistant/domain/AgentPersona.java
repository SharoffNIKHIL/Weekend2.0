package com.weekend.assistant.domain;

import java.util.Objects;

/**
 * Owner-tuned personality and performance of an agent. Sliders are 0–10 except efficiency (1–5).
 *
 * @param humor      0 = strictly matter-of-fact, 10 = playful
 * @param truth      0 = free to brainstorm and speculate, 10 = facts only, says "I don't know", flags uncertainty
 * @param focus      0 = chatty and exploratory, 10 = shortest on-task answer, no tangents
 * @param efficiency 1 Eco … 5 Max: model, tool steps, answer length, memories and history (see {@link Budget})
 */
public record AgentPersona(AgentMode mode, int humor, int truth, int focus, int efficiency, SearchRange search, ApprovalRange approval) {

    public AgentPersona {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(approval, "approval");
        humor = clamp(humor, 0, 10);
        truth = clamp(truth, 0, 10);
        focus = clamp(focus, 0, 10);
        efficiency = clamp(efficiency, 1, 5);
    }

    public static final AgentPersona DEFAULT = preset(AgentMode.WORK);

    /** The four presets. CUSTOM falls back to WORK values. */
    public static AgentPersona preset(AgentMode mode) {
        return switch (mode) {
            case FUNNY -> new AgentPersona(AgentMode.FUNNY, 9, 6, 3, 2, SearchRange.MEMORY, ApprovalRange.WRITES_AND_EXTERNAL);
            case DISCIPLINED -> new AgentPersona(AgentMode.DISCIPLINED, 1, 10, 9, 4, SearchRange.MEMORY, ApprovalRange.ALL);
            case BROWSE -> new AgentPersona(AgentMode.BROWSE, 4, 8, 5, 3, SearchRange.WIDE, ApprovalRange.WRITES_ONLY);
            case WORK, CUSTOM -> new AgentPersona(AgentMode.WORK, 3, 9, 8, 3, SearchRange.WEB, ApprovalRange.WRITES_AND_EXTERNAL);
        };
    }

    /** SERIOUS whenever efficiency is High or Max (focus mode); otherwise by mode. */
    public Mood mood() {
        if (efficiency >= 4) {
            return Mood.SERIOUS;
        }
        return switch (mode) {
            case FUNNY -> Mood.HAPPY;
            case DISCIPLINED -> Mood.SERIOUS;
            case BROWSE -> Mood.CURIOUS;
            case WORK, CUSTOM -> Mood.CALM;
        };
    }

    public boolean focusMode() {
        return efficiency >= 4;
    }

    /** Sampling temperature: humor raises it, truth lowers it (0.0–1.0). */
    public double temperature() {
        double t = 0.5 + (humor - 5) * 0.06 - (truth - 5) * 0.06;
        return Math.round(Math.max(0.0, Math.min(1.0, t)) * 100) / 100.0;
    }

    public Budget budget() {
        return switch (efficiency) {
            case 1 -> new Budget("Eco", false, 2, 512, 4, 8);
            case 2 -> new Budget("Balanced", false, 4, 1024, 6, 12);
            case 3 -> new Budget("Standard", false, 5, 1536, 8, 20);
            case 4 -> new Budget("High", true, 8, 2048, 12, 30);
            default -> new Budget("Max", true, 10, 4096, 16, 40);
        };
    }

    /** What an efficiency level buys: stronger model, more tool steps, longer answers, more context. */
    public record Budget(String label, boolean strongModel, int maxToolSteps, int maxTokens, int memories, int historyTurns) {}

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
