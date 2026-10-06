package com.weekend.assistant.agents;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.AgentConditions;
import com.weekend.assistant.domain.AgentKind;
import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.port.AgentProfileRepository;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.security.SecretFilter;
import com.weekend.assistant.workspace.Inputs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Every agent the owner can pick: Weekend itself (BUILTIN), owner agents with their own instructions and
 * conditions (CUSTOM, from config or created in the app), and other agents reached over HTTPS (REMOTE).
 * Custom instructions never replace Weekend's safety rules; they are added after them.
 */
@Service
public class AgentDirectory {

    public static final String DEFAULT_ID = "weekend";
    public static final int MAX_INSTRUCTIONS = 64 * 1024;
    private static final Logger log = LoggerFactory.getLogger(AgentDirectory.class);

    private final AgentProfileRepository repo;
    private final EndpointPolicy endpoints;
    private final SecretFilter secrets;
    private final AuditLog audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AgentDirectory(AgentProfileRepository repo, SecretFilter secrets, AuditLog audit, WeekendProperties props, Clock clock) {
        this.repo = repo;
        this.secrets = secrets;
        this.audit = audit;
        this.clock = clock;
        this.endpoints = new EndpointPolicy(props.agents().allowedHosts());
        repo.save(new AgentProfile(DEFAULT_ID, "Weekend", "Your private assistant. Default for every chat.", AgentKind.BUILTIN,
                null, "built in", AgentConditions.DEFAULT, null, null, false, clock.instant()));
        props.agents().custom().forEach(this::loadConfigured);
    }

    /** Result of connecting a remote agent: the token is shown to the owner once and only its hash is kept. */
    public record Connection(AgentProfile agent, String inboundToken) {}

    public List<AgentProfile> all() {
        return repo.findAll();
    }

    public Optional<AgentProfile> find(String id) {
        return repo.findById(id == null || id.isBlank() ? DEFAULT_ID : id);
    }

    public AgentProfile createCustom(String name, String description, String instructions, AgentConditions conditions) {
        String text = Inputs.required(instructions, "instructions", MAX_INSTRUCTIONS);
        if (secrets.containsCredential(text)) {
            throw new IllegalArgumentException("instructions must not contain keys, passwords or card numbers");
        }
        AgentProfile a = repo.save(new AgentProfile("custom-" + UUID.randomUUID().toString().substring(0, 8),
                Inputs.required(name, "name", 60), Inputs.optional(description, "description", 300), AgentKind.CUSTOM, text,
                "written in app", sane(conditions), null, null, true, clock.instant()));
        audit.append("owner", "agent.create", a.id());
        return a;
    }

    public Connection connectRemote(String name, String description, String endpoint) {
        String url = endpoints.check(endpoint);
        String token = newToken();
        AgentProfile a = repo.save(new AgentProfile("remote-" + UUID.randomUUID().toString().substring(0, 8),
                Inputs.required(name, "name", 60), Inputs.optional(description, "description", 300), AgentKind.REMOTE, null,
                null, new AgentConditions(List.of(), true, false, 0), url, sha256(token), true, clock.instant()));
        audit.append("owner", "agent.connect", a.id());
        return new Connection(a, token);
    }

    public Optional<AgentProfile> updateConditions(String id, AgentConditions conditions) {
        return repo.findById(id).filter(a -> a.kind() == AgentKind.CUSTOM).map(a -> {
            audit.append("owner", "agent.conditions", id);
            return repo.save(a.withConditions(sane(conditions)));
        });
    }

    public boolean delete(String id) {
        boolean removed = repo.findById(id).filter(AgentProfile::editable).map(a -> repo.deleteById(id)).orElse(false);
        if (removed) {
            audit.append("owner", "agent.delete", id);
        }
        return removed;
    }

    /** Owner-created agents (deleted by delete-all; config and built-in agents stay). */
    public List<AgentProfile> ownerCreated() {
        return repo.findAll().stream().filter(AgentProfile::editable).toList();
    }

    public void deleteOwnerCreated() {
        ownerCreated().forEach(a -> repo.deleteById(a.id()));
    }

    /** Which remote agent holds this inbound token, compared by hash. */
    public Optional<AgentProfile> authenticateInbound(String token) {
        if (token == null || token.length() < 20) {
            return Optional.empty();
        }
        byte[] presented = sha256(token).getBytes(StandardCharsets.US_ASCII);
        return repo.findAll().stream()
                .filter(a -> a.kind() == AgentKind.REMOTE && a.inboundTokenHash() != null)
                .filter(a -> MessageDigest.isEqual(presented, a.inboundTokenHash().getBytes(StandardCharsets.US_ASCII)))
                .findFirst();
    }

    public boolean remoteAllowed() {
        return endpoints.anyAllowed();
    }

    private void loadConfigured(WeekendProperties.CustomAgent c) {
        if (c.instructionsFile() == null || c.instructionsFile().isBlank()) {
            log.info("agent {} skipped: no instructions file configured", c.id());
            return;
        }
        Path file = Path.of(c.instructionsFile());
        String text;
        try {
            if (Files.size(file) > MAX_INSTRUCTIONS) {
                log.warn("agent {} skipped: instructions file is larger than {} bytes", c.id(), MAX_INSTRUCTIONS);
                return;
            }
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("agent {} skipped: instructions file not readable", c.id());
            return;
        }
        if (secrets.containsCredential(text)) {
            log.warn("agent {} skipped: instructions file looks like it contains a secret", c.id());
            return;
        }
        AgentConditions cond = sane(new AgentConditions(c.allowedTools(), c.confirmAllTools(), c.thinkHarder(),
                c.maxToolSteps() == null ? 5 : c.maxToolSteps()));
        repo.save(new AgentProfile(c.id(), c.name(), c.description(), AgentKind.CUSTOM, text, "file: " + file.getFileName(),
                cond, null, null, false, clock.instant()));
        log.info("agent {} loaded ({} characters of instructions)", c.id(), text.length());
    }

    private static AgentConditions sane(AgentConditions c) {
        AgentConditions in = c == null ? AgentConditions.DEFAULT : c;
        return new AgentConditions(in.allowedTools(), in.confirmAllTools(), in.thinkHarder(), Math.min(in.maxToolSteps(), 10));
    }

    private String newToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return "wkd_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
