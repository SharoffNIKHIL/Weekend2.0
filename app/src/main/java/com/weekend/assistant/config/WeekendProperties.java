package com.weekend.assistant.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All runtime settings, bound from {@code application.yml} and environment variables
 * (Cloud Run sets MODEL_DEFAULT, MODEL_STRONG, VERTEX_LOCATION, GCP_PROJECT, ...). No secrets here:
 * the session key comes from Secret Manager through {@link Security#sessionKey()} at runtime.
 */
@Validated
@ConfigurationProperties(prefix = "weekend")
public record WeekendProperties(
        @NotBlank String ownerTimezone,
        Llm llm,
        Agent agent,
        Retention retention,
        Security security,
        Agents agents,
        Pressure pressure,
        Web web,
        Owner owner) {

    public WeekendProperties {
        pressure = pressure == null ? new Pressure(4, Duration.ofSeconds(10), 2, Duration.ofMillis(400)) : pressure;
        web = web == null ? new Web(List.of(), "https://en.wikipedia.org", Duration.ofSeconds(10)) : web;
        owner = owner == null ? new Owner(null, null) : owner;
    }

    public ZoneId zone() {
        return ZoneId.of(ownerTimezone);
    }

    /** LLM routing and pricing (USD per million tokens). */
    public record Llm(
            @NotBlank String provider,
            String gcpProject,
            @NotBlank String vertexLocation,
            @NotBlank String modelDefault,
            @NotBlank String modelStrong,
            @Min(64) int maxOutputTokens,
            BigDecimal defaultInputPrice,
            BigDecimal defaultOutputPrice,
            BigDecimal strongInputPrice,
            BigDecimal strongOutputPrice) {}

    /** Agent loop limits. */
    public record Agent(
            @Min(1) int maxToolSteps,
            @Min(1) int maxMemoriesInContext,
            @Min(1) int maxHistoryTurns,
            @Min(1) int strongModelMinChars,
            BigDecimal dailyCostCapUsd) {}

    /** P5 retention periods. */
    public record Retention(
            Duration messages,
            Duration unpinnedMemories,
            Duration toolCalls,
            Duration auditLog,
            Duration notifications,
            Duration inboxMessages) {}

    /** P1 second layer: signed owner session tokens. */
    public record Security(String sessionKey, Duration sessionTtl, boolean requireSession) {}

    /**
     * Agents (DESIGN §13). {@code allowedHosts}: the only hosts ("host" or "host:port") remote agents may live on;
     * empty = no remote agents. {@code custom}: owner agents defined in config, e.g. one whose instructions come
     * from a CLAUDE.md file read at start-up (never committed).
     */
    public record Agents(List<String> allowedHosts, Duration timeout, List<CustomAgent> custom) {
        public Agents {
            allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
            timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
            custom = custom == null ? List.of() : List.copyOf(custom);
        }
    }

    /** A config-defined custom agent. Empty {@code instructionsFile} = the agent is skipped. */
    public record CustomAgent(
            String id,
            String name,
            String description,
            String instructionsFile,
            List<String> allowedTools,
            boolean confirmAllTools,
            boolean thinkHarder,
            Integer maxToolSteps) {}

    /**
     * High-pressure handling: at most {@code maxConcurrentChats} model calls at once (others wait up to
     * {@code queueWait}, then get HTTP 503 + Retry-After); transient model errors are retried {@code llmRetries} times
     * with exponential backoff starting at {@code retryBackoff}.
     */
    public record Pressure(int maxConcurrentChats, Duration queueWait, int llmRetries, Duration retryBackoff) {
        public Pressure {
            maxConcurrentChats = Math.max(1, maxConcurrentChats);
            queueWait = queueWait == null ? Duration.ofSeconds(10) : queueWait;
            llmRetries = Math.max(0, Math.min(5, llmRetries));
            retryBackoff = retryBackoff == null ? Duration.ofMillis(400) : retryBackoff;
        }
    }

    /**
     * Web search for the agent (🔓 P7: queries leave Weekend). {@code allowedHosts} empty = off (default); adding a host
     * is a security checkpoint. {@code searchBaseUrl}: a MediaWiki API base (Wikipedia by default).
     */
    public record Web(List<String> allowedHosts, String searchBaseUrl, Duration timeout) {
        public Web {
            allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
            searchBaseUrl = searchBaseUrl == null || searchBaseUrl.isBlank() ? "https://en.wikipedia.org" : searchBaseUrl;
            timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        }
    }

    /**
     * Private, local-only owner files (never committed): {@code profileFile} = facts and preferences imported as pinned
     * memories at start-up; {@code brandDir} = a private brand pack (logo.svg, icon.svg, companion.svg) served at /brand/.
     */
    public record Owner(String profileFile, String brandDir) {}
}
