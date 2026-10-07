package com.weekend.assistant.adapter.tts;

import com.google.auth.oauth2.GoogleCredentials;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.port.TtsClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google Cloud Text-to-Speech v1 {@code text:synthesize} with LINEAR16 at 24 kHz (the response includes the WAV header).
 * Auth: the service account through Application Default Credentials, no API key (P4). Commercial use is allowed.
 * 🔓 P7: narration text is sent to Google. Max 5,000 bytes of text per request (Google's limit).
 */
public class GoogleTtsClient implements TtsClient {

    static final int MAX_BYTES = 5000;

    private final WeekendProperties.Tts cfg;
    private final Supplier<String> token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public GoogleTtsClient(WeekendProperties.Tts cfg, Supplier<String> token) {
        this.cfg = cfg;
        this.token = token;
    }

    /** Access token from Application Default Credentials (the Cloud Run service account, or the owner's gcloud login). */
    public static Supplier<String> adcToken() {
        return () -> {
            try {
                GoogleCredentials c = GoogleCredentials.getApplicationDefault()
                        .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
                c.refreshIfExpired();
                return c.getAccessToken().getTokenValue();
            } catch (IOException e) {
                throw new TtsException("Google credentials not available");
            }
        };
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String label() {
        return "Google Cloud TTS (" + cfg.voice() + ")";
    }

    @Override
    public boolean commercialUse() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public byte[] synthesize(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new TtsException("narration chunk is longer than 5,000 bytes");
        }
        Map<String, Object> body = Map.of(
                "input", Map.of("text", text),
                "voice", Map.of("languageCode", cfg.languageCode(), "name", cfg.voice()),
                "audioConfig", Map.of("audioEncoding", "LINEAR16", "sampleRateHertz", 24000, "speakingRate", cfg.speakingRate()));
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.baseUrl() + "/v1/text:synthesize")).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token.get()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        try {
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) {
                throw new TtsException("Google TTS answered HTTP " + res.statusCode()
                        + (res.statusCode() == 403 ? " (enable texttospeech.googleapis.com and grant the service account access)" : ""));
            }
            Object audio = json.readValue(res.body(), Map.class).get("audioContent");
            if (!(audio instanceof String b64)) {
                throw new TtsException("Google TTS returned no audio");
            }
            return Base64.getDecoder().decode(b64);
        } catch (JacksonException e) {
            throw new TtsException("Google TTS did not answer with JSON");
        } catch (IOException e) {
            throw new TtsException("could not reach Google TTS (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsException("interrupted");
        }
    }
}
