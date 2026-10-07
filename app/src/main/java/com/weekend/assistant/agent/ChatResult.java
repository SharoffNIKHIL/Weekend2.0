package com.weekend.assistant.agent;

import com.weekend.assistant.studio.StudioReply;
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
        String featureId,
        String featureName,
        StudioReply studio) {

    public ChatResult(String conversationId, String reply, String model, List<String> toolsUsed, PendingAction pendingConfirmation,
            BigDecimal costUsd, boolean memorySaved, String featureId, String featureName) {
        this(conversationId, reply, model, toolsUsed, pendingConfirmation, costUsd, memorySaved, featureId, featureName, null);
    }
}
