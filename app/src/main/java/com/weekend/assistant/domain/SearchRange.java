package com.weekend.assistant.domain;

/**
 * How far an agent may look for information. OFF = only the conversation; MEMORY = + your memories and tasks;
 * WEB = + web search on allow-listed hosts (top 3 results); WIDE = + more results and page summaries. Web is
 * never possible unless weekend.web.allowed-hosts is set (security checkpoint).
 */
public enum SearchRange {
    OFF,
    MEMORY,
    WEB,
    WIDE
}
