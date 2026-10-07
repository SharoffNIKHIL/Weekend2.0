package com.weekend.assistant.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.SearchRange;
import com.weekend.assistant.port.WebSearchClient;
import com.weekend.assistant.security.SecretFilter;
import com.weekend.assistant.tools.ToolContext;
import com.weekend.assistant.tools.ToolOutput;
import com.weekend.assistant.tools.WebSearchTool;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Web search against a local fake MediaWiki API: request shape, parsing, hardening and the tool's ranges. */
class MediaWikiSearchClientTest {

    private HttpServer server;
    private String base;
    private final List<String> queries = new ArrayList<>();
    private volatile int status = 200;
    private volatile String override;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/w/api.php", ex -> {
            String q = ex.getRequestURI().getRawQuery();
            queries.add(q + " UA=" + ex.getRequestHeaders().getFirst("User-Agent"));
            String body = override != null ? override : q.contains("list=search")
                    ? "{\"query\":{\"search\":[{\"title\":\"Compound interest\",\"snippet\":\"<span class=\\\"searchmatch\\\">Compound</span> interest &quot;A&quot; = P(1+r)\"},"
                      + "{\"title\":\"Interest\",\"snippet\":\"money &amp; time\"}]}}"
                    : "{\"query\":{\"pages\":{\"123\":{\"title\":\"Compound interest\",\"extract\":\"Compound interest is interest on interest.\"}}}}";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
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

    private MediaWikiSearchClient client(List<String> hosts) {
        return new MediaWikiSearchClient(TestFixtures.with(TestFixtures.props(), null,
                new WeekendProperties.Web(hosts, base, Duration.ofSeconds(3)), null));
    }

    @Test
    void searchesParsesAndStripsMarkup() {
        MediaWikiSearchClient c = client(List.of("127.0.0.1"));
        assertThat(c.enabled()).isTrue();
        List<WebSearchClient.Result> r = c.search("compound interest formula", 3);
        assertThat(r).hasSize(2);
        assertThat(r.get(0).title()).isEqualTo("Compound interest");
        assertThat(r.get(0).snippet()).isEqualTo("Compound interest \"A\" = P(1+r)");
        assertThat(r.get(0).url()).isEqualTo(base + "/wiki/Compound_interest");
        assertThat(r.get(1).snippet()).isEqualTo("money & time");
        assertThat(queries.get(0)).contains("list=search").contains("srlimit=3").contains("srsearch=compound+interest+formula")
                .contains("UA=WeekendAssistant/1.0");
        assertThat(c.summary("Compound interest")).isEqualTo("Compound interest is interest on interest.");
    }

    @Test
    void offWithoutAllowedHostsAndRefusesHostsNotOnTheList() {
        MediaWikiSearchClient off = client(List.of());
        assertThat(off.enabled()).isFalse();
        assertThatThrownBy(() -> off.search("x", 3)).hasMessageContaining("switched off");
        assertThat(queries).isEmpty();
        assertThatThrownBy(() -> client(List.of("en.wikipedia.org"))).hasMessageContaining("allowed-hosts"); // base host not listed
    }

    @Test
    void errorsAreReportedSafely() {
        MediaWikiSearchClient c = client(List.of("127.0.0.1"));
        status = 500;
        assertThatThrownBy(() -> c.search("x", 3)).hasMessageContaining("HTTP 500");
        status = 200;
        override = "<html>not json</html>";
        assertThatThrownBy(() -> c.search("x", 3)).hasMessageContaining("did not answer with JSON");
        override = "{\"query\":{}}";
        assertThat(c.search("x", 3)).isEmpty();
        assertThat(c.summary("x")).isEmpty();
    }

    @Test
    void toolRespectsRangesRedactsSecretsAndWrapsResults() {
        WebSearchTool tool = new WebSearchTool(client(List.of("127.0.0.1")), new SecretFilter());
        assertThat(tool.external()).isTrue();
        assertThat(tool.writes()).isFalse();
        assertThat(tool.available()).isTrue();

        ToolOutput web = tool.execute(Map.of("query", "compound interest"), new ToolContext("c", "m", SearchRange.WEB));
        assertThat(web.content()).contains("1. Compound interest").doesNotContain("Summary:");
        ToolOutput wide = tool.execute(Map.of("query", "compound interest"), new ToolContext("c", "m", SearchRange.WIDE));
        assertThat(wide.content()).contains("Summary: Compound interest is interest on interest.");
        assertThat(queries).anyMatch(q -> q.contains("srlimit=6"));

        assertThat(tool.execute(Map.of("query", "x"), new ToolContext("c", "m", SearchRange.MEMORY)).isError()).isTrue();
        int before = queries.size();
        tool.execute(Map.of("query", "my key AKIAABCDEFGHIJKLMNOP"), new ToolContext("c", "m", SearchRange.WEB));
        assertThat(queries.subList(before, queries.size())).allMatch(q -> !q.contains("AKIA"));
        assertThat(tool.execute(Map.of("query", "x".repeat(201)), new ToolContext("c", "m")).isError()).isTrue();

        WebSearchTool off = new WebSearchTool(client(List.of()), new SecretFilter());
        assertThat(off.available()).isFalse();
        assertThat(off.execute(Map.of("query", "x"), new ToolContext("c", "m")).content()).contains("switched off");
    }
}
