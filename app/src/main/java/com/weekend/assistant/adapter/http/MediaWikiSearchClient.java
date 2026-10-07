package com.weekend.assistant.adapter.http;

import com.weekend.assistant.agents.EndpointPolicy;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.port.WebSearchClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Web search through a MediaWiki API (Wikipedia by default): {@code /w/api.php?action=query&list=search} for results and
 * {@code prop=extracts} for a plain-text intro. Same hardening as the agent client: allow-listed host only, https (http
 * on loopback for tests), no redirects, timeouts, 256 KiB cap. Identifies itself with a User-Agent, as Wikimedia asks.
 */
@Component
public class MediaWikiSearchClient implements WebSearchClient {

    static final int MAX_BYTES = 256 * 1024;

    private final EndpointPolicy policy;
    private final String base;
    private final Duration timeout;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public MediaWikiSearchClient(WeekendProperties props) {
        this.policy = new EndpointPolicy(props.web().allowedHosts());
        this.timeout = props.web().timeout();
        this.enabled = policy.anyAllowed();
        this.base = enabled ? policy.check(props.web().searchBaseUrl()) : props.web().searchBaseUrl();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Result> search(String query, int limit) {
        Map<String, Object> body = get("action=query&list=search&srprop=snippet&format=json&utf8=1&srlimit="
                + Math.max(1, Math.min(10, limit)) + "&srsearch=" + enc(query));
        List<Result> out = new ArrayList<>();
        Object q = body.get("query");
        if (q instanceof Map<?, ?> qm && qm.get("search") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("title") instanceof String title) {
                    String snippet = m.get("snippet") instanceof String s ? plain(s) : "";
                    out.add(new Result(title, base + "/wiki/" + enc(title.replace(' ', '_')), snippet));
                }
            }
        }
        return out;
    }

    @Override
    public String summary(String title) {
        Map<String, Object> body = get("action=query&prop=extracts&exintro=1&explaintext=1&redirects=1&format=json&titles=" + enc(title));
        if (body.get("query") instanceof Map<?, ?> q && q.get("pages") instanceof Map<?, ?> pages) {
            for (Object p : pages.values()) {
                if (p instanceof Map<?, ?> page && page.get("extract") instanceof String ex) {
                    return ex.length() > 1500 ? ex.substring(0, 1497) + "..." : ex;
                }
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String query) {
        if (!enabled) {
            throw new WebSearchException("web search is switched off (no allowed hosts)");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/w/api.php?" + query)).timeout(timeout)
                .header("User-Agent", "WeekendAssistant/1.0 (private single-owner assistant)").GET().build();
        try {
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                byte[] bytes = in.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw new WebSearchException("search answer too large");
                }
                if (res.statusCode() / 100 != 2) {
                    throw new WebSearchException("search answered HTTP " + res.statusCode());
                }
                return json.readValue(bytes, Map.class);
            }
        } catch (JacksonException e) {
            throw new WebSearchException("search did not answer with JSON");
        } catch (IOException e) {
            throw new WebSearchException("could not reach the search service (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebSearchException("interrupted");
        }
    }

    static String plain(String html) {
        return html.replaceAll("<[^>]*>", "").replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").strip();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
