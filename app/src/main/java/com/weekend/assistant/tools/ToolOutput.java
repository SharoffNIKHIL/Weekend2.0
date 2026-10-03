package com.weekend.assistant.tools;

/** What a tool returns to the agent. */
public record ToolOutput(String content, boolean isError) {

    public static ToolOutput ok(String content) {
        return new ToolOutput(content, false);
    }

    public static ToolOutput error(String message) {
        return new ToolOutput(message, true);
    }
}
