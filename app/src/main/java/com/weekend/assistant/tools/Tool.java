package com.weekend.assistant.tools;

import java.util.Map;

/**
 * A plugin the agent can call. Tools are fixed, reviewed code in this repo; nothing is downloaded
 * at runtime (DESIGN §13). Any tool with {@link #writes()} = true pauses for owner confirmation.
 */
public interface Tool {

    /** Stable name the model uses, e.g. {@code memory_search}. */
    String name();

    String description();

    /** JSON Schema for the input object. */
    Map<String, Object> inputSchema();

    /** True when the tool changes something (creates, sends, deletes). */
    boolean writes();

    ToolOutput execute(Map<String, Object> input, ToolContext context);
}
