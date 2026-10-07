package com.weekend.assistant.features;

import java.util.List;

/**
 * A plugin (runs inside Weekend) or connector (talks to an outside service, 🔓 P7) that a feature can attach.
 * {@code tools} are the agent tools it brings; vision and art need no tools (Claude reads images and writes SVG itself).
 */
public enum Capability {
    TIME("Clock", Kind.PLUGIN, "Current date and time in IST", List.of("current_time")),
    MEMORY("Memory", Kind.PLUGIN, "Your profile and saved memories", List.of("memory_search", "memory_save")),
    TASKS("Tasks and reminders", Kind.PLUGIN, "Read and add tasks and reminders (adding asks first)",
            List.of("task_list", "task_create", "reminder_create")),
    MATH("Math engine", Kind.PLUGIN, "Exact local arithmetic, equation solving and statistics", List.of("math_evaluate", "math_solve", "math_stats")),
    FINANCE("Payments", Kind.PLUGIN, "Read your tracked payments (never moves money)", List.of("payment_list")),
    VISION("Image analysis", Kind.PLUGIN, "Claude reads the images you attach: objects, text, charts, details", List.of()),
    ART("Art studio", Kind.PLUGIN, "Claude draws original vector art (SVG); export as 4K PNG or SVG", List.of()),
    WEB("Web research", Kind.CONNECTOR, "Wikipedia search and summaries (off until a host is allowed)", List.of("web_search")),
    NOTION("Notion", Kind.CONNECTOR, "Search your Notion and save notes (off until a token is set)", List.of("notion_search", "notion_create_page"));

    public enum Kind { PLUGIN, CONNECTOR }

    private final String label;
    private final Kind kind;
    private final String description;
    private final List<String> tools;

    Capability(String label, Kind kind, String description, List<String> tools) {
        this.label = label;
        this.kind = kind;
        this.description = description;
        this.tools = tools;
    }

    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    public String description() {
        return description;
    }

    public List<String> tools() {
        return tools;
    }
}
