package com.weekend.assistant.tools;

import com.weekend.assistant.agents.AgentDirectory;
import com.weekend.assistant.domain.AgentKind;
import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.inbox.InboxService;
import com.weekend.assistant.port.RemoteAgentClient;
import com.weekend.assistant.security.SecretFilter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Sends a message to a connected remote agent and returns its reply (also saved to Messages).
 * 🔓 The message leaves Weekend, so writes = true: the owner approves every call. Secrets are redacted first.
 */
@Component
public class AgentDelegateTool implements Tool {

    private final AgentDirectory agents;
    private final RemoteAgentClient client;
    private final InboxService inbox;
    private final SecretFilter secrets;

    public AgentDelegateTool(AgentDirectory agents, RemoteAgentClient client, InboxService inbox, SecretFilter secrets) {
        this.agents = agents;
        this.client = client;
        this.inbox = inbox;
        this.secrets = secrets;
    }

    @Override
    public String name() {
        return "agent_delegate";
    }

    @Override
    public String description() {
        return "Send a message to one of the owner's connected remote agents (by agent_id) and get its reply.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of(
                        "agent_id", Schemas.string("Id of a connected remote agent"),
                        "message", Schemas.string("What to send; never include secrets")),
                List.of("agent_id", "message"));
    }

    @Override
    public boolean writes() {
        return true;
    }

    @Override
    public boolean external() {
        return true;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        String id = Schemas.requireString(input, "agent_id");
        String message = secrets.redact(Schemas.requireString(input, "message"));
        Optional<AgentProfile> agent = agents.find(id).filter(a -> a.kind() == AgentKind.REMOTE);
        if (agent.isEmpty()) {
            return ToolOutput.error("No connected remote agent with that id.");
        }
        try {
            String reply = client.send(agent.get(), message, context.conversationId());
            inbox.receive(agent.get().id(), agent.get().name(), "Reply: " + preview(message), reply);
            return ToolOutput.ok(secrets.redact(reply));
        } catch (RemoteAgentClient.RemoteAgentException e) {
            return ToolOutput.error(agent.get().name() + ": " + e.getMessage());
        }
    }

    private static String preview(String s) {
        String t = s.strip().replaceAll("\\s+", " ");
        return t.length() <= 60 ? t : t.substring(0, 57) + "...";
    }
}
