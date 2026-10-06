package com.weekend.assistant.agent;

import java.math.BigDecimal;
import java.util.List;

/** What the API returns for one chat turn. */
public record ChatResult(
        String conversationId,
        String reply,
        String model,
        List<String> toolsUsed,
        PendingAction pendingConfirmation,
        BigDecimal costUsd,
        boolean memorySaved,
        String agentId,
        String agentName) {}
