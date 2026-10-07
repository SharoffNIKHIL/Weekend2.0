package com.weekend.assistant.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.port.NotionClient;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Notion REST against a local fake: headers, request bodies, parsing and errors. The token is never echoed. */
class HttpNotionClientTest {

    private HttpServer server;
    private String base;
    private final List<String> seen = new ArrayList<>();
    private volatile int status = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.add(ex.getRequestURI().getPath() + " auth=" + ex.getRequestHeaders().getFirst("Authorization")
                    + " ver=" + ex.getRequestHeaders().getFirst("Notion-Version") + " body=" + body);
            String res = ex.getRequestURI().getPath().endsWith("/search")
                    ? "{\"results\":[{\"id\":\"abc\",\"url\":\"https://www.notion.so/abc\",\"properties\":{\"Name\":{\"type\":\"title\",\"title\":[{\"plain_text\":\"Weekly \"},{\"plain_text\":\"review\"}]}}},"
                      + "{\"id\":\"def\",\"url\":\"https://www.notion.so/def\",\"properties\":{}}]}"
                    : "{\"id\":\"new1\",\"url\":\"https://www.notion.so/new1\"}";
            byte[] b = (status == 200 ? res : "{\"message\":\"secret detail\"}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpNotionClient client(String token, String parent) {
        return new HttpNotionClient(TestFixtures.withNotion(new WeekendProperties.Notion(token, base, null, parent, Duration.ofSeconds(3))));
    }

    @Test
    void searchesPagesWithBearerTokenAndVersion() {
        List<NotionClient.Page> pages = client("secret_test_token", null).search("review", 5);
        assertThat(pages).extracting(NotionClient.Page::title).containsExactly("Weekly review", "(untitled)");
        assertThat(pages.get(0).url()).isEqualTo("https://www.notion.so/abc");
        assertThat(seen.get(0)).startsWith("/v1/search auth=Bearer secret_test_token ver=2022-06-28")
                .contains("\"query\":\"review\"").contains("\"page_size\":5").contains("\"value\":\"page\"");
    }

    @Test
    void createsPagesUnderTheParentInChunkedParagraphs() {
        String longPara = "x".repeat(4000);
        NotionClient.Page p = client("t0ken_value_1234", "parent-1").createPage("Groceries", "milk, eggs\n\n" + longPara);
        assertThat(p.url()).isEqualTo("https://www.notion.so/new1");
        String body = seen.get(0);
        assertThat(body).startsWith("/v1/pages").contains("\"page_id\":\"parent-1\"").contains("\"content\":\"Groceries\"");
        assertThat(body.split("\"type\":\"paragraph\"", -1)).hasSize(1 + 1 + 3); // 1 short + 4000 chars in 3 chunks of ≤1900
        assertThatThrownBy(() -> client("t0ken_value_1234", null).createPage("x", "y")).hasMessageContaining("parent page");
    }

    @Test
    void offWithoutTokenAndSafeErrors() {
        HttpNotionClient off = client(null, null);
        assertThat(off.enabled()).isFalse();
        assertThatThrownBy(() -> off.search("x", 3)).hasMessageContaining("not connected");
        assertThat(seen).isEmpty();
        status = 401;
        assertThatThrownBy(() -> client("bad_token_123456", null).search("x", 3)).hasMessageContaining("refused the token")
                .hasMessageNotContaining("secret detail");
        status = 500;
        assertThatThrownBy(() -> client("tok_123456789012", null).search("x", 3)).hasMessageContaining("HTTP 500");
        assertThat(new WeekendProperties.Notion("tok_123456789012", null, null, null, null).toString()).doesNotContain("tok_");
    }

    @Test
    void onlyTheNotionApiHostIsAllowed() {
        assertThatThrownBy(() -> new HttpNotionClient(TestFixtures.withNotion(
                new WeekendProperties.Notion("t", "https://evil.example.com", null, null, null)))).hasMessageContaining("api.notion.com");
        assertThat(new HttpNotionClient(TestFixtures.withNotion(new WeekendProperties.Notion(null, "https://evil.example.com", null, null, null)))
                .enabled()).isFalse(); // off: never calls anything
    }
}
