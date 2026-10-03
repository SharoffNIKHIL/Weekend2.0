package com.weekend.assistant.domain;

import java.time.Instant;

/** Log entry for one tool execution. Arguments are stored redacted. */
public record ToolCallRecord(
        String id,
        String messageId,
        String tool,
        String argsRedacted,
        int resultSize,
        boolean confirmedByOwner,
        Instant createdAt) {}
