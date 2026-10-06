package com.weekend.assistant.adapter.http;

import com.weekend.assistant.agents.EndpointPolicy;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.AgentKind;
import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.port.RemoteAgentClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * "weekend-agent/1" over HTTP(S). Hardening: endpoint re-checked against the allow-list on every call, no
 * redirects (SSRF), connect and request timeouts, response capped at 64 KiB, only the reply text is used.
 */
@Component
public class HttpRemoteAgentClient implements RemoteAgentClient {

    static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final HttpClient http;
    private final EndpointPolicy policy;
    private final Duration timeout;
    private final JsonMapper json = JsonMapper.builder().build();

    public HttpRemoteAgentClient(WeekendProperties props) {
        this.timeout = props.agents().timeout();
        this.policy = new EndpointPolicy(props.agents().allowedHosts());
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public String send(AgentProfile agent, String message, String conversationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("conversationId", conversationId);
        body.put("from", "weekend");
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint(agent) + "/message"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", "weekend-agent/1")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        String text = call(req);
        try {
            Object reply = json.readValue(text, Map.class).get("reply");
            if (!(reply instanceof String s)) {
                throw new RemoteAgentException("the agent's answer has no \"reply\" text");
            }
            return s;
        } catch (JacksonException e) {
            throw new RemoteAgentException("the agent did not answer with JSON");
        }
    }

    @Override
    public boolean healthy(AgentProfile agent) {
        try {
            call(HttpRequest.newBuilder(URI.create(endpoint(agent) + "/health")).timeout(Duration.ofSeconds(5)).GET().build());
            return true;
        } catch (RemoteAgentException e) {
            return false;
        }
    }

    private String endpoint(AgentProfile agent) {
        if (agent.kind() != AgentKind.REMOTE || agent.endpoint() == null) {
            throw new RemoteAgentException("not a remote agent");
        }
        try {
            return policy.check(agent.endpoint());
        } catch (IllegalArgumentException e) {
            throw new RemoteAgentException("endpoint no longer allowed: " + e.getMessage());
        }
    }

    private String call(HttpRequest req) {
        try {
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                byte[] bytes = in.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new RemoteAgentException("the agent's answer is larger than 64 KiB");
                }
                if (res.statusCode() / 100 != 2) {
                    throw new RemoteAgentException("the agent answered HTTP " + res.statusCode());
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RemoteAgentException("could not reach the agent (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteAgentException("interrupted");
        }
    }
}
