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
        Notion notion,
        Pressure pressure,
        Web web,
        Owner owner,
        Studio studio) {

    public WeekendProperties {
        pressure = pressure == null ? new Pressure(4, Duration.ofSeconds(10), 2, Duration.ofMillis(400)) : pressure;
        web = web == null ? new Web(List.of(), "https://en.wikipedia.org", Duration.ofSeconds(10)) : web;
        owner = owner == null ? new Owner(null, null) : owner;
        notion = notion == null ? new Notion(null, null, null, null, null) : notion;
        studio = studio == null ? new Studio(null, null, 0, 0, 0, null, null, 0, true) : studio;
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
     * Notion connector (🔓 P7: note text leaves Weekend). Off until {@code token} is set (an internal-integration secret
     * from Notion, kept in Secret Manager / env WEEKEND_NOTION_TOKEN — never in code). New pages go under
     * {@code parentPageId}. {@code version} is the Notion-Version header.
     */
    public record Notion(String token, String baseUrl, String version, String parentPageId, Duration timeout) {
        public Notion {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.notion.com" : baseUrl;
            version = version == null || version.isBlank() ? "2022-06-28" : version;
            timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
        }

        public boolean enabled() {
            return token != null && !token.isBlank();
        }

        @Override
        public String toString() {
            return "Notion[baseUrl=" + baseUrl + ", version=" + version + ", token=" + (enabled() ? "****" : "none") + "]";
        }
    }

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

    /**
     * Weekend Studio (YouTube video creation). {@code mediaDir}: rendered videos (private, P5 {@code retention}).
     * {@code webSearches}: Claude web searches per research step (0 = off; 🔓 P7, paid per search). {@code scale} &lt; 1 renders
     * smaller for tests/previews. Voice comes from {@code tts}.
     */
    public record Studio(String mediaDir, String ffmpeg, int fps, double scale, int renderThreads, Duration retention, Tts tts,
            int webSearches, boolean music) {
        public Studio {
            mediaDir = mediaDir == null || mediaDir.isBlank() ? System.getProperty("java.io.tmpdir") + "/weekend-studio" : mediaDir;
            ffmpeg = ffmpeg == null || ffmpeg.isBlank() ? "ffmpeg" : ffmpeg;
            fps = fps <= 0 ? 30 : Math.min(60, fps);
            scale = scale <= 0 ? 1.0 : Math.min(1.0, scale);
            renderThreads = renderThreads <= 0 ? 2 : Math.min(4, renderThreads);
            retention = retention == null ? Duration.ofDays(30) : retention;
            tts = tts == null ? new Tts(null, null, null, 0, null, null) : tts;
            webSearches = Math.max(0, Math.min(10, webSearches));
        }
    }

    /**
     * Narration voice. {@code provider}: none | google (Cloud Text-to-Speech, commercial use allowed, 🔓 P7) |
     * say (macOS voices: drafts only, Apple's licence is personal/non-commercial).
     */
    public record Tts(String provider, String voice, String languageCode, double speakingRate, String baseUrl, String sayVoice) {
        public Tts {
            provider = provider == null || provider.isBlank() ? "none" : provider.strip().toLowerCase(java.util.Locale.ROOT);
            voice = voice == null || voice.isBlank() ? "en-IN-Neural2-B" : voice;
            languageCode = languageCode == null || languageCode.isBlank() ? "en-IN" : languageCode;
            speakingRate = speakingRate <= 0 ? 1.0 : Math.max(0.5, Math.min(1.5, speakingRate));
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://texttospeech.googleapis.com" : baseUrl;
            sayVoice = sayVoice == null || sayVoice.isBlank() ? "Aman" : sayVoice;
        }
    }
}
