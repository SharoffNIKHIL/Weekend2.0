package com.weekend.assistant.port;

import java.util.List;
import java.util.Map;

/**
 * Port to a large language model. Implementations: {@code ScriptedLlmProvider} (local/tests),
 * {@code VertexClaudeProvider} (Claude on Vertex AI). Swappable without touching the agent (DESIGN §7.3).
 */
public interface LlmProvider {

    LlmResponse complete(LlmRequest request);

    /** A tool the model may call. {@code inputSchema} is a JSON Schema object. */
    record ToolSpec(String name, String description, Map<String, Object> inputSchema) {}

    /** A tool call requested by the model. */
    record ToolUse(String id, String name, Map<String, Object> input) {}

    /** The result we send back for a tool call. Content is DATA, never instructions. */
    record ToolResult(String toolUseId, String content, boolean isError) {}

    /** One element of the conversation sent to the model. */
    sealed interface Turn permits UserText, AssistantTurn, ToolResults {}

    /** An image sent with a user turn (Claude vision). {@code base64} has no data-URL prefix. */
    record ImagePart(String mediaType, String base64) {}

    record UserText(String text, List<ImagePart> images) implements Turn {
        public UserText {
            images = images == null ? List.of() : List.copyOf(images);
        }

        public UserText(String text) {
            this(text, List.of());
        }
    }

    record AssistantTurn(String text, List<ToolUse> toolUses) implements Turn {}

    record ToolResults(List<ToolResult> results) implements Turn {}

    /** {@code temperature} 0.0–1.0, or null for the provider default. */
    record LlmRequest(String model, String system, List<Turn> turns, List<ToolSpec> tools, int maxTokens, Double temperature) {

        public LlmRequest(String model, String system, List<Turn> turns, List<ToolSpec> tools, int maxTokens) {
            this(model, system, turns, tools, maxTokens, null);
        }
    }

    record LlmResponse(String text, List<ToolUse> toolUses, String stopReason, int inputTokens, int outputTokens) {
        public boolean wantsTools() {
            return toolUses != null && !toolUses.isEmpty();
        }
    }
}
