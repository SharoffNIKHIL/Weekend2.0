package com.weekend.assistant.adapter.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Tool;
import com.weekend.assistant.port.LlmProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks our domain → Anthropic SDK mapping without any network call. */
class VertexClaudeProviderMappingTest {

    @Test
    void mapsTurnsToMessages() {
        List<MessageParam> msgs = VertexClaudeProvider.toMessages(List.of(
                new LlmProvider.UserText("what time is it?"),
                new LlmProvider.AssistantTurn("", List.of(new LlmProvider.ToolUse("toolu_1", "current_time", Map.of()))),
                new LlmProvider.ToolResults(List.of(new LlmProvider.ToolResult("toolu_1", "<data>10:00</data>", false)))));
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0).role()).isEqualTo(MessageParam.Role.USER);
        assertThat(msgs.get(1).role()).isEqualTo(MessageParam.Role.ASSISTANT);
        assertThat(msgs.get(2).role()).isEqualTo(MessageParam.Role.USER);
        assertThat(msgs.get(1).content().blockParams()).get().asList().hasSize(1);
        assertThat(msgs.get(2).content().blockParams()).get().asList().hasSize(1);
    }

    @Test
    void mapsToolSpecWithRequiredFields() {
        Tool tool = VertexClaudeProvider.toTool(new LlmProvider.ToolSpec("memory_search", "Search memories",
                Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")), "required", List.of("query"))));
        assertThat(tool.name()).isEqualTo("memory_search");
        assertThat(tool.inputSchema().required()).get().asList().containsExactly("query");
    }
}
