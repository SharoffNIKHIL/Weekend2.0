package com.weekend.assistant.domain;

/**
 * Which tool calls wait for the owner's yes. ALL = every tool; WRITES_AND_EXTERNAL = anything that changes data or
 * leaves Weekend (web search, other agents) — the default; WRITES_ONLY = web searches on allow-listed hosts run
 * without asking (standing approval for research). Writes and messages to other agents always ask.
 */
public enum ApprovalRange {
    ALL,
    WRITES_AND_EXTERNAL,
    WRITES_ONLY
}
