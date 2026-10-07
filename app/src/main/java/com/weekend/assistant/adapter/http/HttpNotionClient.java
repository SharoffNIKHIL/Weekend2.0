package com.weekend.assistant.adapter.http;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.net.EndpointPolicy;
import com.weekend.assistant.port.NotionClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Notion REST API: {@code POST /v1/search} (pages only) and {@code POST /v1/pages}. Bearer token from config
 * (Secret Manager / env), never logged. Hardening: only the configured Notion host (or loopback in tests), no redirects,
 * timeouts, 512 KiB cap, error bodies are not echoed. Notion-Version is configurable (default 2022-06-28).
 */
@Component
public class HttpNotionClient implements NotionClient {

    static final int MAX_BYTES = 512 * 1024;
    static final int CHUNK = 1900; // Notion limits one rich-text object to 2,000 characters

    private final WeekendProperties.Notion cfg;
    private final String base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public HttpNotionClient(WeekendProperties props) {
        this.cfg = props.notion();
        String host = URI.create(cfg.baseUrl()).getHost();
        this.base = cfg.enabled() ? new EndpointPolicy(List.of(host == null ? "" : host)).check(cfg.baseUrl()) : cfg.baseUrl();
        if (cfg.enabled() && !("api.notion.com".equals(host) || "127.0.0.1".equals(host) || "localhost".equals(host))) {
            throw new IllegalStateException("weekend.notion.base-url must be https://api.notion.com");
        }
    }

    @Override
    public boolean enabled() {
        return cfg.enabled();
    }

    @Override
    public List<Page> search(String query, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("page_size", Math.max(1, Math.min(10, limit)));
        body.put("filter", Map.of("property", "object", "value", "page"));
        Map<String, Object> res = post("/v1/search", body);
        List<Page> out = new ArrayList<>();
        if (res.get("results") instanceof List<?> results) {
            for (Object o : results) {
                if (o instanceof Map<?, ?> page) {
                    out.add(new Page(String.valueOf(page.get("id")), title(page), String.valueOf(page.get("url"))));
                }
            }
        }
        return out;
    }

    @Override
    public Page createPage(String title, String text) {
        if (cfg.parentPageId() == null || cfg.parentPageId().isBlank()) {
            throw new NotionException("no parent page configured (WEEKEND_NOTION_PARENT_PAGE)");
        }
        List<Object> children = new ArrayList<>();
        for (String para : text.split("\\n\\s*\\n")) {
            String p = para.strip();
            for (int i = 0; i < p.length() && children.size() < 90; i += CHUNK) {
                String chunk = p.substring(i, Math.min(p.length(), i + CHUNK));
                children.add(Map.of("object", "block", "type", "paragraph",
                        "paragraph", Map.of("rich_text", List.of(Map.of("type", "text", "text", Map.of("content", chunk))))));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parent", Map.of("page_id", cfg.parentPageId()));
        body.put("properties", Map.of("title", Map.of("title", List.of(Map.of("text", Map.of("content", title))))));
        body.put("children", children);
        Map<String, Object> res = post("/v1/pages", body);
        return new Page(String.valueOf(res.get("id")), title, String.valueOf(res.get("url")));
    }

    static String title(Map<?, ?> page) {
        if (page.get("properties") instanceof Map<?, ?> props) {
            for (Object v : props.values()) {
                if (v instanceof Map<?, ?> prop && "title".equals(prop.get("type")) && prop.get("title") instanceof List<?> parts) {
                    StringBuilder sb = new StringBuilder();
                    for (Object part : parts) {
                        if (part instanceof Map<?, ?> m && m.get("plain_text") instanceof String t) {
                            sb.append(t);
                        }
                    }
                    return sb.isEmpty() ? "(untitled)" : sb.toString();
                }
            }
        }
        return "(untitled)";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        if (!enabled()) {
            throw new NotionException("Notion is not connected (no token configured)");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path)).timeout(cfg.timeout())
                .header("Authorization", "Bearer " + cfg.token())
                .header("Notion-Version", cfg.version())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                byte[] bytes = in.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw new NotionException("Notion answer too large");
                }
                if (res.statusCode() == 401 || res.statusCode() == 403) {
                    throw new NotionException("Notion refused the token or the page is not shared with the integration (HTTP " + res.statusCode() + ")");
                }
                if (res.statusCode() / 100 != 2) {
                    throw new NotionException("Notion answered HTTP " + res.statusCode());
                }
                return json.readValue(bytes, Map.class);
            }
        } catch (JacksonException e) {
            throw new NotionException("Notion did not answer with JSON");
        } catch (IOException e) {
            throw new NotionException("could not reach Notion (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotionException("interrupted");
        }
    }
}
