package com.weekend.assistant.tools;

import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.memory.MemoryService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Searches the owner's saved memories. */
@Component
public class MemorySearchTool implements Tool {

    private final MemoryService memories;

    public MemorySearchTool(MemoryService memories) {
        this.memories = memories;
    }

    @Override
    public String name() {
        return "memory_search";
    }

    @Override
    public String description() {
        return "Search the owner's saved memories (facts, preferences, tasks) by keywords.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of("query", Schemas.string("What to look for")), List.of("query"));
    }

    @Override
    public boolean writes() {
        return false;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        List<Memory> found = memories.search(Schemas.requireString(input, "query"), 8);
        if (found.isEmpty()) {
            return ToolOutput.ok("No matching memories.");
        }
        return ToolOutput.ok(found.stream().map(m -> "- " + m.text()).collect(Collectors.joining("\n")));
    }
}
