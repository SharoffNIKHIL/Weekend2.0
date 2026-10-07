package com.weekend.assistant.port;

import java.util.Optional;

/** Openly licensed photos for videos (e.g. Wikimedia Commons). 🔓 The search words leave Weekend (P7). */
public interface StockImageClient {

    /** A downloaded image with what is needed to credit it in the video description. */
    record StockImage(byte[] bytes, String title, String author, String licence, String pageUrl) {
        public String credit() {
            return "\"" + title + "\" by " + author + ", " + licence + " — " + pageUrl;
        }
    }

    boolean enabled();

    Optional<StockImage> find(String query, boolean landscape);
}
