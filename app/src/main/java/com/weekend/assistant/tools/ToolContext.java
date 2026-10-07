package com.weekend.assistant.tools;

import com.weekend.assistant.domain.SearchRange;

/** Per-call context passed to tools. {@code searchRange} comes from the active agent's persona. */
public record ToolContext(String conversationId, String messageId, SearchRange searchRange) {

    public ToolContext {
        searchRange = searchRange == null ? SearchRange.WEB : searchRange;
    }

    public ToolContext(String conversationId, String messageId) {
        this(conversationId, messageId, SearchRange.WEB);
    }
}
