package com.weekend.assistant.domain;

/** BUILTIN = Weekend itself; CUSTOM = owner-written instructions on the same model; REMOTE = another agent over HTTPS. */
public enum AgentKind {
    BUILTIN,
    CUSTOM,
    REMOTE
}
