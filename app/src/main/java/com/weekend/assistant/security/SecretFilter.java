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

    /** 13–19 digits with optional spaces or dashes; only Luhn-valid runs count as card numbers (PCI: never stored). */
    private static final Pattern CARD = Pattern.compile("(?<![0-9])[0-9](?:[ -]?[0-9]){12,18}(?![0-9])");

    /**
     * High-confidence credentials only (key formats, PEM, service-account JSON, card numbers), without the
     * "password: …" keyword heuristic. For long owner-written text such as agent instructions, which talk about secrets.
     */
    public boolean containsCredential(String text) {
        return text != null && (PATTERNS.subList(0, PATTERNS.size() - 1).stream().anyMatch(p -> p.matcher(text).find()) || containsCard(text));
    }

    public boolean containsSecret(String text) {
        return text != null && (PATTERNS.stream().anyMatch(p -> p.matcher(text).find()) || containsCard(text));
    }

    public String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = text;
        for (Pattern p : PATTERNS) {
            out = p.matcher(out).replaceAll("[REDACTED]");
        }
        return CARD.matcher(out).replaceAll(m -> luhn(m.group()) ? "[REDACTED]" : m.group());
    }

    private static boolean containsCard(String text) {
        return CARD.matcher(text).results().anyMatch(m -> luhn(m.group()));
    }

    static boolean luhn(String candidate) {
        String digits = candidate.replaceAll("[ -]", "");
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int d = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                d = d * 2 > 9 ? d * 2 - 9 : d * 2;
            }
            sum += d;
        }
        return sum % 10 == 0;
    }
}
