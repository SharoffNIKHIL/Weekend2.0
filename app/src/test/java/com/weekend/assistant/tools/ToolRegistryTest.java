package com.weekend.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    record FakeTool(String name, boolean writes) implements Tool {
        @Override public String description() { return "fake"; }
        @Override public Map<String, Object> inputSchema() { return Map.of("type", "object", "properties", Map.of()); }
        @Override public ToolOutput execute(Map<String, Object> input, ToolContext context) { return ToolOutput.ok("ok"); }
    }

    @Test
    void exposesSpecsInOrder() {
        ToolRegistry r = new ToolRegistry(List.of(new FakeTool("alpha_tool", false), new FakeTool("beta_tool", true)));
        assertThat(r.names()).containsExactly("alpha_tool", "beta_tool");
        assertThat(r.specs()).extracting("name").containsExactly("alpha_tool", "beta_tool");
        assertThat(r.find("beta_tool")).get().extracting(Tool::writes).isEqualTo(true);
        assertThat(r.find("missing")).isEmpty();
    }

    @Test
    void rejectsDuplicatesAndBadNames() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(new FakeTool("same_name", false), new FakeTool("same_name", false))))
                .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> new ToolRegistry(List.of(new FakeTool("Bad-Name", false)))).hasMessageContaining("invalid");
    }
}
