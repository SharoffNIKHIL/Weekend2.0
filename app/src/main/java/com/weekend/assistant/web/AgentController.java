package com.weekend.assistant.web;

import com.weekend.assistant.agents.AgentDirectory;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.AgentConditions;
import com.weekend.assistant.domain.AgentKind;
import com.weekend.assistant.domain.AgentMode;
import com.weekend.assistant.domain.AgentPersona;
import com.weekend.assistant.domain.Mood;
import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.port.RemoteAgentClient;
import com.weekend.assistant.tools.ToolRegistry;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agents: list and pick, create custom agents with conditions, connect remote agents. Instruction text is only
 * returned by the dedicated /instructions endpoint; inbound tokens are shown once, at connect time.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentDirectory agents;
    private final RemoteAgentClient client;
    private final ToolRegistry tools;
    private final WeekendProperties props;

    public AgentController(AgentDirectory agents, RemoteAgentClient client, ToolRegistry tools, WeekendProperties props) {
        this.agents = agents;
        this.client = client;
        this.tools = tools;
        this.props = props;
    }

    /** What the UI shows for an agent. {@code dataLeavesIndia}: its prompts or messages are processed outside India (P7). */
    public record AgentView(String id, String name, String description, AgentKind kind, String instructionsSource,
            String instructionsTitle, int instructionsLines, int instructionsChars, AgentConditions conditions,
            AgentPersona persona, Mood mood, boolean focusMode, AgentPersona.Budget budget, double temperature,
            String endpointHost, boolean editable, boolean dataLeavesIndia) {}

    /** A preset as the UI shows it. */
    public record ModeView(AgentMode mode, AgentPersona persona, Mood mood) {}

    public record CustomRequest(String name, String description, String instructions, AgentConditions conditions) {}

    public record RemoteRequest(String name, String description, String endpoint) {}

    public record Catalog(List<AgentView> agents, List<String> tools, boolean remoteAllowed, boolean webAllowed, List<ModeView> modes) {}

    @GetMapping
    public Catalog list() {
        List<ModeView> modes = java.util.Arrays.stream(AgentMode.values()).filter(m -> m != AgentMode.CUSTOM)
                .map(m -> new ModeView(m, AgentPersona.preset(m), AgentPersona.preset(m).mood())).toList();
        return new Catalog(agents.all().stream().map(this::view).toList(), tools.names(), agents.remoteAllowed(),
                !props.web().allowedHosts().isEmpty(), modes);
    }

    @GetMapping("/{id}/instructions")
    public ResponseEntity<Map<String, String>> instructions(@PathVariable String id) {
        return agents.find(id).filter(a -> a.instructions() != null)
                .map(a -> ResponseEntity.ok(Map.of("text", a.instructions())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/custom")
    public ResponseEntity<AgentView> createCustom(@RequestBody CustomRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(view(agents.createCustom(req.name(), req.description(), req.instructions(), req.conditions())));
    }

    /** Returns the inbound token once; Weekend keeps only its hash. */
    @PostMapping("/remote")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody RemoteRequest req) {
        AgentDirectory.Connection c = agents.connectRemote(req.name(), req.description(), req.endpoint());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("agent", view(c.agent()), "inboundToken", c.inboundToken(),
                "inboundUrl", "/agent-inbox"));
    }

    @PutMapping("/{id}/conditions")
    public ResponseEntity<AgentView> conditions(@PathVariable String id, @RequestBody AgentConditions req) {
        return ResponseEntity.of(agents.updateConditions(id, req).map(this::view));
    }

    @PutMapping("/{id}/persona")
    public ResponseEntity<AgentView> persona(@PathVariable String id, @RequestBody AgentPersona req) {
        return ResponseEntity.of(agents.updatePersona(id, req).map(this::view));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable String id) {
        return agents.find(id).filter(a -> a.kind() == AgentKind.REMOTE)
                .map(a -> ResponseEntity.ok(Map.<String, Object>of("healthy", client.healthy(a))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return agents.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private AgentView view(AgentProfile a) {
        String text = a.instructions();
        String title = text == null ? null : text.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst()
                .map(l -> l.replaceFirst("^#+\\s*", "")).map(l -> l.length() > 90 ? l.substring(0, 87) + "..." : l).orElse(null);
        String host = a.endpoint() == null ? null : URI.create(a.endpoint()).getHost();
        boolean vertexAbroad = "vertex".equals(props.llm().provider()) && !props.llm().vertexLocation().startsWith("asia-south");
        boolean leaves = a.kind() == AgentKind.REMOTE || vertexAbroad;
        AgentPersona p = a.persona();
        return new AgentView(a.id(), a.name(), a.description(), a.kind(), a.instructionsSource(), title,
                text == null ? 0 : (int) text.lines().count(), text == null ? 0 : text.length(), a.conditions(),
                p, p.mood(), p.focusMode(), p.budget(), p.temperature(), host, a.editable(), leaves);
    }
}
