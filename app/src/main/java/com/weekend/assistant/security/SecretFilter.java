package com.weekend.assistant.security;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects and redacts secrets so they never reach memory, logs or tool-call records (P4).
 * Pattern-based; the Phase 3 memory extractor adds an LLM check on top.
 */
@Component
public class SecretFilter {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),                                   // AWS access key id
            Pattern.compile("AIza[0-9A-Za-z_\\-]{35}"),                            // Google API key
            Pattern.compile("tskey-[A-Za-z0-9\\-_]{10,}"),                         // Tailscale key
            Pattern.compile("sk-[A-Za-z0-9\\-_]{16,}"),                            // generic sk- API keys
            Pattern.compile("gh[pousr]_[A-Za-z0-9]{30,}"),                         // GitHub tokens
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),                 // PEM private keys
            Pattern.compile("\"private_key\"\\s*:"),                                // service-account JSON
            Pattern.compile("(?i)\\b(password|passwd|pwd|secret|token|api[_ ]?key)\\s*(is|=|:)\\s*\\S+"));

    public boolean containsSecret(String text) {
        return text != null && PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    public String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = text;
        for (Pattern p : PATTERNS) {
            out = p.matcher(out).replaceAll("[REDACTED]");
        }
        return out;
    }
}
