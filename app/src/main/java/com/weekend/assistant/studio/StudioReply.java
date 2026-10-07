package com.weekend.assistant.studio;

import java.util.List;

/**
 * What the Studio answers in chat. ASK = one question with tap-able options; CONFIRM = the plan, waiting for "Start";
 * STARTED = rendering began ({@code projectId} to follow); INFO = anything else.
 */
public record StudioReply(Kind kind, String text, List<String> options, List<String> plan, String projectId) {

    public enum Kind { ASK, CONFIRM, STARTED, INFO }

    public StudioReply {
        options = options == null ? List.of() : List.copyOf(options);
        plan = plan == null ? List.of() : List.copyOf(plan);
    }
}
