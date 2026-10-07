package com.weekend.assistant.adapter.tts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.port.TtsClient;
import com.weekend.assistant.studio.Wav;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Google Cloud TTS against a local fake: request shape (voice, LINEAR16 24 kHz, bearer token), decoding and errors. */
class GoogleTtsClientTest {

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private volatile int status = 200;
    private WeekendProperties.Tts cfg;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/text:synthesize", ex -> {
            requests.add(ex.getRequestMethod() + " " + ex.getRequestHeaders().getFirst("Authorization") + " "
                    + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String audio = Base64.getEncoder().encodeToString(Wav.write(Wav.silence(0.5)));
            byte[] b = ("{\"audioContent\":\"" + audio + "\"}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        cfg = new WeekendProperties.Tts("google", "en-IN-Neural2-B", "en-IN", 1.05, "http://127.0.0.1:" + server.getAddress().getPort(), null);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void synthesisesLinear16() {
        GoogleTtsClient c = new GoogleTtsClient(cfg, () -> "fake-token");
        assertThat(c.enabled()).isTrue();
        assertThat(c.commercialUse()).isTrue();
        assertThat(c.label()).contains("en-IN-Neural2-B");
        short[] pcm = Wav.read(c.synthesize("Pods are the smallest unit."));
        assertThat(Wav.seconds(pcm)).isEqualTo(0.5);
        assertThat(requests.get(0)).startsWith("POST Bearer fake-token ").contains("\"audioEncoding\":\"LINEAR16\"")
                .contains("\"sampleRateHertz\":24000").contains("\"name\":\"en-IN-Neural2-B\"").contains("Pods are the smallest unit.");
    }

    @Test
    void reportsErrorsWithoutLeakingTheToken() {
        status = 403;
        GoogleTtsClient c = new GoogleTtsClient(cfg, () -> "fake-token");
        assertThatThrownBy(() -> c.synthesize("hi")).isInstanceOf(TtsClient.TtsException.class)
                .hasMessageContaining("403").hasMessageContaining("texttospeech.googleapis.com").hasMessageNotContaining("fake-token");
        assertThatThrownBy(() -> c.synthesize("x".repeat(5001))).hasMessageContaining("5,000 bytes");
    }
}
