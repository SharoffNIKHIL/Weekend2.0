package com.weekend.assistant.port;

import java.util.List;

/** The owner's Notion workspace (🔓 P7). Implementations are off until a token is configured. */
public interface NotionClient {

    record Page(String id, String title, String url) {}

    boolean enabled();

    List<Page> search(String query, int limit);

    /** Creates a page under the configured parent page; returns it. */
    Page createPage(String title, String body);

    class NotionException extends RuntimeException {
        public NotionException(String message) {
            super(message);
        }
    }
}
