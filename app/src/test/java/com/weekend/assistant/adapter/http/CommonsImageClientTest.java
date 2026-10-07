package com.weekend.assistant.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.port.StockImageClient;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Wikimedia Commons photos against a local fake: only free licences (no BY-SA, NC, ND), size and orientation filters, credits. */
class CommonsImageClientTest {

    private HttpServer server;
    private String base;
    private final List<String> queries = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/w/api.php", ex -> {
            queries.add(ex.getRequestURI().getRawQuery());
            String body = "{\"query\":{\"pages\":{"
                    + page(1, "File:Fox share.jpg", "CC BY-SA 4.0", 1920, 1280, "image/jpeg") + ","
                    + page(2, "File:Fox nc.jpg", "CC BY-NC 2.0", 1920, 1280, "image/jpeg") + ","
                    + page(3, "File:Fox small.jpg", "CC0", 320, 200, "image/jpeg") + ","
                    + page(4, "File:Fox anim.gif", "CC0", 1920, 1280, "image/gif") + ","
                    + page(5, "File:Arctic fox.jpg", "CC BY 2.0", 1920, 1280, "image/jpeg") + "}}}";
            send(ex, body.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/img/", ex -> send(ex, Base64.getDecoder().decode(TestFixtures.png(800, 500, Color.GRAY))));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String page(int index, String title, String licence, int w, int h, String mime) {
        return "\"" + index + "\":{\"index\":" + index + ",\"title\":\"" + title + "\",\"imageinfo\":[{\"mime\":\"" + mime + "\",\"width\":" + w
                + ",\"height\":" + h + ",\"thumburl\":\"" + base + "/img/" + index + "\",\"descriptionurl\":\"" + base + "/wiki/" + index
                + "\",\"extmetadata\":{\"LicenseShortName\":{\"value\":\"" + licence + "\"},\"Artist\":{\"value\":\"<a href='x'>Jane Doe</a>\"}}}]}";
    }

    private static void send(com.sun.net.httpserver.HttpExchange ex, byte[] b) throws IOException {
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private CommonsImageClient client(List<String> hosts) {
        return new CommonsImageClient(TestFixtures.with(TestFixtures.props(), null, new WeekendProperties.Web(hosts, null, Duration.ofSeconds(3)), null), base);
    }

    @Test
    void picksTheFirstFreelyLicensedPhotoAndCreditsIt() {
        CommonsImageClient c = client(List.of("127.0.0.1"));
        assertThat(c.enabled()).isTrue();
        Optional<StockImageClient.StockImage> img = c.find("arctic fox", true);
        assertThat(img).isPresent();
        assertThat(img.get().title()).isEqualTo("Arctic fox");
        assertThat(img.get().licence()).isEqualTo("CC BY 2.0");
        assertThat(img.get().author()).isEqualTo("Jane Doe");
        assertThat(img.get().credit()).contains("Arctic fox").contains("Jane Doe").contains("CC BY 2.0");
        assertThat(img.get().bytes()).isNotEmpty();
        assertThat(queries.get(0)).contains("generator=search").contains("gsrnamespace=6").contains("arctic+fox");
        assertThat(c.find("arctic fox", false)).isPresent(); // no portrait photo: falls back to a landscape one (cropped)
    }

    @Test
    void offUntilTheHostIsAllowedAndLicenceRules() {
        assertThat(client(List.of()).enabled()).isFalse();
        assertThat(client(List.of()).find("fox", true)).isEmpty();
        assertThat(queries).isEmpty();
        assertThat(CommonsImageClient.allowedLicence("CC0")).isTrue();
        assertThat(CommonsImageClient.allowedLicence("Public domain")).isTrue();
        assertThat(CommonsImageClient.allowedLicence("CC BY 4.0")).isTrue();
        assertThat(CommonsImageClient.allowedLicence("CC BY-SA 3.0")).isFalse();
        assertThat(CommonsImageClient.allowedLicence("CC BY-NC-ND 2.0")).isFalse();
        assertThat(CommonsImageClient.allowedLicence("GFDL")).isFalse();
    }
}
