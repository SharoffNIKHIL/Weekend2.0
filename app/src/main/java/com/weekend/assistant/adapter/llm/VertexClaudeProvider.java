package com.weekend.assistant.adapter.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.vertex.backends.VertexBackend;
import com.google.auth.oauth2.GoogleCredentials;
import com.weekend.assistant.port.LlmProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude on Vertex AI (weekend.llm.provider=vertex). Authenticates with the Cloud Run service
 * account through Application Default Credentials; no API key (P4).
 * 🔓 P7: with location "global", prompts may be processed outside India (DESIGN §7.4).
 */
public class VertexClaudeProvider implements LlmProvider {

    private final AnthropicClient client;

    public VertexClaudeProvider(String project, String location) {
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new UncheckedIOException("Application Default Credentials not available", e);
        }
        this.client = AnthropicOkHttpClient.builder()
                .backend(VertexBackend.builder().googleCredentials(credentials).region(location).project(project).build())
                .build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(request.maxTokens())
                .system(request.system())
                .messages(toMessages(request.turns()));
        request.tools().forEach(t -> params.addTool(toTool(t)));
        Message res = client.messages().create(params.build());

        StringBuilder text = new StringBuilder();
        List<ToolUse> uses = new ArrayList<>();
        for (ContentBlock block : res.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
            block.toolUse().ifPresent(u -> uses.add(new ToolUse(u.id(), u.name(), toMap(u._input()))));
        }
        String stop = res.stopReason().map(Object::toString).orElse("unknown");
        return new LlmResponse(text.toString(), uses, stop, (int) res.usage().inputTokens(), (int) res.usage().outputTokens());
    }

    static List<MessageParam> toMessages(List<Turn> turns) {
        List<MessageParam> out = new ArrayList<>();
        for (Turn turn : turns) {
            if (turn instanceof UserText u) {
                out.add(MessageParam.builder().role(MessageParam.Role.USER).content(u.text()).build());
            } else if (turn instanceof AssistantTurn a) {
                List<ContentBlockParam> blocks = new ArrayList<>();
                if (a.text() != null && !a.text().isBlank()) {
                    blocks.add(ContentBlockParam.ofText(a.text()));
                }
                for (ToolUse use : a.toolUses()) {
                    blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                            .id(use.id())
                            .name(use.name())
                            .input(ToolUseBlockParam.Input.builder().putAllAdditionalProperties(toJson(use.input())).build())
                            .build()));
                }
                out.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT).contentOfBlockParams(blocks).build());
            } else if (turn instanceof ToolResults r) {
                List<ContentBlockParam> blocks = r.results().stream()
                        .map(res -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                                .toolUseId(res.toolUseId())
                                .content(res.content())
                                .isError(res.isError())
                                .build()))
                        .toList();
                out.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Tool toTool(ToolSpec spec) {
        Map<String, Object> schema = spec.inputSchema();
        Map<String, Object> props = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder().putAllAdditionalProperties(toJson(props)).build())
                        .required(required)
                        .build())
                .build();
    }

    private static Map<String, JsonValue> toJson(Map<String, Object> map) {
        Map<String, JsonValue> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(k, JsonValue.from(v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonValue value) {
        Map<String, Object> converted = value.convert(Map.class);
        return converted == null ? Map.of() : converted;
    }
}
