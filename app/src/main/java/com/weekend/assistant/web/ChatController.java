package com.weekend.assistant.web;

import com.weekend.assistant.agent.AgentService;
import com.weekend.assistant.agent.ChatGuard;
import com.weekend.assistant.agent.ChatResult;
import com.weekend.assistant.agent.PendingAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chat API: send a message, list and answer pending write-tool confirmations. */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final AgentService agent;
    private final ChatGuard guard;

    public ChatController(AgentService agent, ChatGuard guard) {
        this.agent = agent;
        this.guard = guard;
    }

    public record ChatRequest(String conversationId, @NotBlank @Size(max = 8000) String message, Boolean thinkHarder,
            @Size(max = 64) String agentId) {}

    public record ConfirmRequest(boolean approved) {}

    @PostMapping("/chat")
    public ChatResult chat(@Valid @RequestBody ChatRequest req) {
        return guard.run(() -> agent.chat(req.conversationId(), req.message(), Boolean.TRUE.equals(req.thinkHarder()), req.agentId()));
    }

    @GetMapping("/pending")
    public List<PendingAction> pending() {
        return agent.pendingActions();
    }

    @PostMapping("/confirm/{id}")
    public ResponseEntity<Map<String, String>> confirm(@PathVariable String id, @RequestBody ConfirmRequest req) {
        return agent.confirm(id, req.approved())
                .map(result -> ResponseEntity.ok(Map.of("result", result)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
