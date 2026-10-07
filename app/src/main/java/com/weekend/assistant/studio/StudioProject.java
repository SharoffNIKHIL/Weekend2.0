package com.weekend.assistant.studio;

import java.time.Instant;
import java.util.List;

/** One request: a single video or a series, made from a brief and a plan. */
public record StudioProject(String id, String conversationId, VideoBrief brief, SeriesPlan plan, List<String> jobIds, Instant createdAt) {

    public StudioProject {
        jobIds = List.copyOf(jobIds);
    }
}
