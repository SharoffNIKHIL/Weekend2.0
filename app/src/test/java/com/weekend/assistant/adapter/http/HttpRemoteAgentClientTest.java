package com.weekend.assistant.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.domain.AgentConditions;
import com.weekend.assistant.domain.AgentKind;
import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.port.RemoteAgentClient.RemoteAgentException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real HTTP against a loopback server: protocol, error handling and hardening. */
class HttpRemoteAgentClientTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final HttpRemoteAgentClient client = new HttpRemoteAgentClient(
            TestFixtures.props(TestFixtures.agents(List.of("127.0.0.1"), List.of())));

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        route("/ok/message", 200, "{\"reply\":\"hello owner\"}");
        route("/ok/health", 200, "{}");
        route("/err/message", 500, "{\"reply\":\"x\"}");
        route("/err/health", 503, "");
        route("/html/message", 200, "<html>nope</html>");
        route("/noreply/message", 200, "{\"answer\":\"x\"}");
        route("/big/message", 200, "{\"reply\":\"" + "x".repeat(HttpRemoteAgentClient.MAX_RESPONSE_BYTES) + "\"}");
        server.createContext("/redirect/message", ex -> {
            ex.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void route(String path, int status, String body) {
        server.createContext(path, ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(b);
                }
            }
            ex.close();
        });
    }

    private AgentProfile remote(String path) {
        return new AgentProfile("remote-1", "Helper", null, AgentKind.REMOTE, null, null,
                new AgentConditions(List.of(), true, false, 0), base + path, "hash", true, Instant.now());
    }

    @Test
    void sendsTheProtocolMessageAndReturnsTheReply() {
        assertThat(client.send(remote("/ok"), "hi there", "conv-1")).isEqualTo("hello owner");
        assertThat(lastBody.get()).contains("\"message\":\"hi there\"").contains("\"conversationId\":\"conv-1\"")
                .contains("\"from\":\"weekend\"");
        assertThat(client.healthy(remote("/ok"))).isTrue();
    }

    @Test
    void reportsFailuresWithoutEchoingTheResponse() {
        assertThatThrownBy(() -> client.send(remote("/err"), "x", null)).isInstanceOf(RemoteAgentException.class).hasMessageContaining("HTTP 500");
        assertThatThrownBy(() -> client.send(remote("/html"), "x", null)).hasMessageContaining("did not answer with JSON");
        assertThatThrownBy(() -> client.send(remote("/noreply"), "x", null)).hasMessageContaining("no \"reply\"");
        assertThatThrownBy(() -> client.send(remote("/big"), "x", null)).hasMessageContaining("64 KiB");
        assertThat(client.healthy(remote("/err"))).isFalse();
    }

    @Test
    void neverFollowsRedirects() {
        assertThatThrownBy(() -> client.send(remote("/redirect"), "x", null)).hasMessageContaining("HTTP 302");
    }

    @Test
    void unreachableOrNoLongerAllowedEndpointsFailSafely() {
        server.stop(0);
        assertThatThrownBy(() -> client.send(remote("/ok"), "x", null)).hasMessageContaining("could not reach");
        HttpRemoteAgentClient locked = new HttpRemoteAgentClient(TestFixtures.props());   // empty allow-list
        assertThatThrownBy(() -> locked.send(remote("/ok"), "x", null)).hasMessageContaining("no longer allowed");
        AgentProfile notRemote = new AgentProfile("c", "C", null, AgentKind.CUSTOM, "x", null, AgentConditions.DEFAULT,
                null, null, true, Instant.now());
        assertThatThrownBy(() -> client.send(notRemote, "x", null)).hasMessageContaining("not a remote agent");
    }
}
