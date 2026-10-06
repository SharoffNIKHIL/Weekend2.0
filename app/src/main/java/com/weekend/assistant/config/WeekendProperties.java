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
        Agents agents) {

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
}
