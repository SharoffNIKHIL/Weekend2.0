package com.weekend.assistant.adapter.http;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.net.EndpointPolicy;
import com.weekend.assistant.port.StockImageClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Openly licensed photos from Wikimedia Commons (MediaWiki API, file namespace). Only CC0, public-domain and CC BY
 * images are used — share-alike (BY-SA) and non-commercial licences are skipped, because they would put conditions on
 * the whole video. Each image comes with its credit for the video description. Needs both commons.wikimedia.org and
 * upload.wikimedia.org on weekend.web.allowed-hosts (security checkpoint); no redirects; 8 MB per image.
 */
@Component
public class CommonsImageClient implements StockImageClient {

    static final int MAX_JSON = 512 * 1024;
    static final int MAX_IMAGE = 8 * 1024 * 1024;

    private final EndpointPolicy policy;
    private final String base;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public CommonsImageClient(WeekendProperties props) {
        this(props, "https://commons.wikimedia.org");
    }

    /** {@code base}: the MediaWiki API host (a local fake in tests). */
    public CommonsImageClient(WeekendProperties props, String b) {
        List<String> hosts = props.web().allowedHosts();
        this.policy = new EndpointPolicy(hosts);
        boolean ok;
        try {
            policy.check(b);
            ok = true;
        } catch (IllegalArgumentException e) {
            ok = false;
        }
        this.enabled = ok;
        this.base = b;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    static boolean allowedLicence(String licence) {
        String l = licence == null ? "" : licence.toLowerCase(Locale.ROOT);
        if (l.contains("sa") && l.contains("by") || l.contains("nc") || l.contains("nd") || l.contains("fair use")) {
            return false;
        }
        return l.startsWith("cc0") || l.contains("public domain") || l.startsWith("pd") || l.startsWith("cc by ") || l.equals("cc by")
                || l.matches("cc[- ]by[- ]\\d(\\.\\d)?");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<StockImage> find(String query, boolean landscape) {
        if (!enabled || query == null || query.isBlank()) {
            return Optional.empty();
        }
        String q = URLEncoder.encode(query.strip() + " filetype:bitmap", StandardCharsets.UTF_8);
        Map<String, Object> res = getJson(base + "/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=12"
                + "&prop=imageinfo&iiprop=url%7Cextmetadata%7Csize%7Cmime&iiurlwidth=1920&gsrsearch=" + q);
        if (!(res.get("query") instanceof Map<?, ?> qm) || !(qm.get("pages") instanceof Map<?, ?> pages)) {
            return Optional.empty();
        }
        List<Map<?, ?>> candidates = new java.util.ArrayList<>();
        for (Object p : pages.values()) {
            if (p instanceof Map<?, ?> page) {
                candidates.add(page);
            }
        }
        candidates.sort(java.util.Comparator.comparingInt(p -> p.get("index") instanceof Number n ? n.intValue() : 999));
        // first pass: photos in the video's orientation; second pass: any orientation (the frame crops to fit)
        for (int pass = 0; pass < 2; pass++) {
            Optional<StockImage> found = pick(candidates, landscape, pass == 0);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<StockImage> pick(List<Map<?, ?>> candidates, boolean landscape, boolean strict) {
        for (Map<?, ?> page : candidates) {
            if (!(page.get("imageinfo") instanceof List<?> infos) || infos.isEmpty() || !(infos.get(0) instanceof Map<?, ?> info)) {
                continue;
            }
            String mime = String.valueOf(info.get("mime"));
            int w = info.get("width") instanceof Number n ? n.intValue() : 0;
            int h = info.get("height") instanceof Number n ? n.intValue() : 0;
            if (!(mime.equals("image/jpeg") || mime.equals("image/png")) || w < 640 || (strict && (landscape ? w < h : h < w))) {
                continue;
            }
            Map<?, ?> meta = info.get("extmetadata") instanceof Map<?, ?> m ? m : Map.of();
            String licence = value(meta, "LicenseShortName");
            if (!allowedLicence(licence)) {
                continue;
            }
            String thumb = String.valueOf(info.get("thumburl") != null ? info.get("thumburl") : info.get("url"));
            byte[] bytes;
            try {
                bytes = get(policy.check(thumb), MAX_IMAGE);
            } catch (RuntimeException e) {
                continue;
            }
            String title = String.valueOf(page.get("title")).replaceFirst("^File:", "").replaceAll("\\.[A-Za-z]{3,4}$", "");
            String author = MediaWikiSearchClient.plain(value(meta, "Artist"));
            return Optional.of(new StockImage(bytes, title, author.isBlank() ? "Unknown author" : author, licence,
                    String.valueOf(info.get("descriptionurl"))));
        }
        return Optional.empty();
    }

    private static String value(Map<?, ?> meta, String key) {
        return meta.get(key) instanceof Map<?, ?> m && m.get("value") != null ? String.valueOf(m.get("value")) : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String url) {
        try {
            return json.readValue(get(url, MAX_JSON), Map.class);
        } catch (JacksonException e) {
            return Map.of();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private byte[] get(String url, int max) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "WeekendStudio/1.0 (private single-owner assistant)").GET().build();
        try {
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                byte[] bytes = in.readNBytes(max + 1);
                if (bytes.length > max || res.statusCode() / 100 != 2) {
                    throw new IllegalStateException("bad response");
                }
                return bytes;
            }
        } catch (IOException e) {
            throw new IllegalStateException("unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        }
    }
}
