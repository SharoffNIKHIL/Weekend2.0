package com.weekend.assistant.config;

import com.weekend.assistant.adapter.llm.RetryingLlmProvider;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.adapter.llm.VertexClaudeProvider;
import com.weekend.assistant.port.LlmProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the LLM adapter: "local" (offline, default) or "vertex" (Claude on Vertex AI). */
@Configuration
public class LlmConfig {

    @Bean
    public LlmProvider llmProvider(WeekendProperties props) {
        WeekendProperties.Pressure p = props.pressure();
        return new RetryingLlmProvider(base(props), p.llmRetries(), p.retryBackoff(), d -> Thread.sleep(d.toMillis()));
    }

    private static LlmProvider base(WeekendProperties props) {
        WeekendProperties.Llm llm = props.llm();
        return switch (llm.provider()) {
            case "local" -> new ScriptedLlmProvider();
            case "vertex" -> {
                if (llm.gcpProject() == null || llm.gcpProject().isBlank()) {
                    throw new IllegalStateException("weekend.llm.gcp-project (GCP_PROJECT) is required for the vertex provider");
                }
                yield new VertexClaudeProvider(llm.gcpProject(), llm.vertexLocation());
            }
            default -> throw new IllegalStateException("unknown weekend.llm.provider: " + llm.provider());
        };
    }
}
