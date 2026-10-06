package com.weekend.assistant.port;

import com.weekend.assistant.domain.AgentProfile;

/**
 * Talks to another agent over HTTPS ("weekend-agent/1" protocol, DESIGN §13):
 * {@code POST <endpoint>/message {"message","conversationId","from":"weekend"}} → {@code {"reply": "..."}};
 * {@code GET <endpoint>/health} → 2xx. Every call is a 🔓 data exit and needs the owner's yes first.
 */
public interface RemoteAgentClient {

    /** Sends one message and returns the other agent's reply text. Throws {@link RemoteAgentException} on failure. */
    String send(AgentProfile agent, String message, String conversationId);

    /** True when the agent answers its health check. */
    boolean healthy(AgentProfile agent);

    class RemoteAgentException extends RuntimeException {
        public RemoteAgentException(String message) {
            super(message);
        }
    }
}
