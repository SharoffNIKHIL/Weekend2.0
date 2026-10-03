package com.weekend.assistant.tools;

import com.weekend.assistant.domain.MemoryKind;
import com.weekend.assistant.memory.MemoryService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Saves a memory the owner asked to keep. Secrets are rejected by {@code MemoryService}. */
@Component
public class MemorySaveTool implements Tool {

    private final MemoryService memories;

    public MemorySaveTool(MemoryService memories) {
        this.memories = memories;
    }

    @Override
    public String name() {
        return "memory_save";
    }

    @Override
    public String description() {
        return "Save a short fact, preference or task the owner asked you to remember. Never save passwords or keys.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of(
                        "text", Schemas.string("The fact to remember, one sentence"),
                        "kind", Map.of("type", "string", "enum", List.of("fact", "preference", "task"))),
                List.of("text"));
    }

    @Override
    public boolean writes() {
        return false; // owner-requested memory; visible and deletable in the app (P6)
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        String text = Schemas.requireString(input, "text");
        Object kindValue = input.get("kind");
        MemoryKind kind = kindValue instanceof String k ? MemoryKind.valueOf(k.toUpperCase(Locale.ROOT)) : MemoryKind.FACT;
        return memories.remember(text, kind, context.messageId())
                .map(m -> ToolOutput.ok("Saved memory " + m.id()))
                .orElseGet(() -> ToolOutput.error("Not saved: it looks like a secret. Store secrets in a password manager."));
    }
}
