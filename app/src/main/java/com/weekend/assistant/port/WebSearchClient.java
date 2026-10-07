package com.weekend.assistant.port;

import java.util.List;

/** Read-only web search for the agent. 🔓 P7: the query leaves Weekend. Implementations enforce the host allow-list. */
public interface WebSearchClient {

    record Result(String title, String url, String snippet) {}

    /** True when at least one host is allowed (weekend.web.allowed-hosts). */
    boolean enabled();

    List<Result> search(String query, int limit);

    /** A plain-text summary of one result (first paragraph), or empty text. */
    String summary(String title);

    class WebSearchException extends RuntimeException {
        public WebSearchException(String message) {
            super(message);
        }
    }
}
