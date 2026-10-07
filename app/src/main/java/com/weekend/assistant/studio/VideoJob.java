package com.weekend.assistant.studio;

import java.time.Instant;

/** One video being made. Immutable; the service replaces it as the job moves through its stages. */
public record VideoJob(String id, String projectId, int number, int total, String title, Status status, String stage, int progress,
        String error, Script script, Double seconds, String voice, boolean draftVoice, Instant createdAt, Instant readyAt) {

    public enum Status { QUEUED, WORKING, READY, FAILED }

    public VideoJob with(Status s, String st, int p) {
        return new VideoJob(id, projectId, number, total, title, s, st, Math.max(0, Math.min(100, p)), error, script, seconds, voice, draftVoice,
                createdAt, readyAt);
    }

    public VideoJob withScript(Script sc) {
        return new VideoJob(id, projectId, number, total, sc.title(), status, stage, progress, error, sc, seconds, voice, draftVoice, createdAt, readyAt);
    }

    public VideoJob ready(double secs, String voiceLabel, boolean draft, Instant at) {
        return new VideoJob(id, projectId, number, total, title, Status.READY, "Ready", 100, null, script, secs, voiceLabel, draft, createdAt, at);
    }

    public VideoJob failed(String message) {
        return new VideoJob(id, projectId, number, total, title, Status.FAILED, "Failed", progress, message, script, seconds, voice, draftVoice,
                createdAt, readyAt);
    }
}
