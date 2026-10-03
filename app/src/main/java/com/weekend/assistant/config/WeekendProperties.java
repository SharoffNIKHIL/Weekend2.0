package com.weekend.assistant.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
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
        Security security) {

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
            Duration auditLog) {}

    /** P1 second layer: signed owner session tokens. */
    public record Security(String sessionKey, Duration sessionTtl, boolean requireSession) {}
}
