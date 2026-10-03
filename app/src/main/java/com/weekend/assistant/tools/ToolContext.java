package com.weekend.assistant.tools;

/** Per-call context passed to tools. */
public record ToolContext(String conversationId, String messageId) {}
