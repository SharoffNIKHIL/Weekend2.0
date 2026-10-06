package com.weekend.assistant.web;

import com.weekend.assistant.agents.AgentDirectory;
import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.inbox.InboxService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound messages from connected agents: {@code POST /agent-inbox} with {@code Authorization: Bearer <inbound token>}.
 * Outside /api (agents have no owner session) and only reachable where the network allows (Cloud Run internal
 * ingress + tailnet). Messages land in the inbox as DATA; nothing is executed. Max 60 per agent per hour.
 */
@RestController
public class AgentInboxController {

    static final int PER_HOUR = 60;

    private final AgentDirectory agents;
    private final InboxService inbox;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AgentInboxController(AgentDirectory agents, InboxService inbox, Clock clock) {
        this.agents = agents;
        this.inbox = inbox;
        this.clock = clock;
    }

    public record InboundMessage(String subject, String body) {}

    private record Window(Instant start, int count) {}

    @PostMapping("/agent-inbox")
    public ResponseEntity<Map<String, Object>> receive(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) InboundMessage msg) {
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7).strip() : null;
        Optional<AgentProfile> agent = agents.authenticateInbound(token);
        if (agent.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        if (msg == null || msg.body() == null || msg.body().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "body is required"));
        }
        if (!allow(agent.get().id())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "rate limit: " + PER_HOUR + " per hour"));
        }
        inbox.receive(agent.get().id(), agent.get().name(), msg.subject(), msg.body());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("accepted", true));
    }

    private boolean allow(String agentId) {
        Instant now = clock.instant();
        Window w = windows.compute(agentId, (k, old) -> old == null || Duration.between(old.start(), now).toHours() >= 1
                ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        return w.count() <= PER_HOUR;
    }
}
