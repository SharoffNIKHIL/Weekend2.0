package com.weekend.assistant.agent;

import java.time.Instant;
import java.util.Map;

/** A write-tool call waiting for the owner's yes/no. */
public record PendingAction(String id, String conversationId, String tool, Map<String, Object> input, String summary, Instant createdAt) {}
