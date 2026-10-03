package com.weekend.assistant.tools;

import com.weekend.assistant.port.LlmProvider.ToolSpec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Allow-list of tools: every {@link Tool} bean, with unique, well-formed names. */
@Component
public class ToolRegistry {

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]{2,63}$");

    private final Map<String, Tool> tools;

    public ToolRegistry(List<Tool> discovered) {
        Map<String, Tool> map = new LinkedHashMap<>();
        for (Tool t : discovered) {
            if (!NAME.matcher(t.name()).matches()) {
                throw new IllegalStateException("invalid tool name: " + t.name());
            }
            if (map.putIfAbsent(t.name(), t) != null) {
                throw new IllegalStateException("duplicate tool name: " + t.name());
            }
        }
        this.tools = Collections.unmodifiableMap(map);
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolSpec> specs() {
        return tools.values().stream().map(t -> new ToolSpec(t.name(), t.description(), t.inputSchema())).toList();
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }
}
