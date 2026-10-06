package com.weekend.assistant.adapter.llm;

import com.weekend.assistant.port.LlmProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic, offline LLM for local runs and tests (weekend.llm.provider=local). No data leaves
 * the machine. It recognises a few intents so the agent loop and tools can be exercised end to end:
 * "time"/"date" → current_time, "what do you know about X" → memory_search, "remind me" → reminder_create,
 * "add task X" → task_create, "list my tasks" → task_list, "delegate to <agent_id>: X" → agent_delegate.
 */
public class ScriptedLlmProvider implements LlmProvider {

    @Override
    public LlmResponse complete(LlmRequest request) {
        Turn last = request.turns().get(request.turns().size() - 1);
        if (last instanceof ToolResults results) {
            String combined = results.results().stream().map(ToolResult::content).reduce("", (a, b) -> a + b);
            return new LlmResponse("Here is what I found:\n" + combined, List.of(), "end_turn", 50, 20);
        }
        String text = last instanceof UserText u ? u.text() : "";
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("add task ")) {
            return tool("task_create", Map.of("title", text.substring("add task ".length())));
        }
        if (lower.startsWith("list my tasks") || lower.startsWith("what are my tasks")) {
            return tool("task_list", Map.of());
        }
        if (lower.startsWith("delegate to ") && text.indexOf(':') > "delegate to ".length()) {
            int colon = text.indexOf(':');
            return tool("agent_delegate", Map.of("agent_id", text.substring("delegate to ".length(), colon).strip(),
                    "message", text.substring(colon + 1).strip()));
        }
        if (lower.contains("time") || lower.contains("date")) {
            return tool("current_time", Map.of());
        }
        if (lower.startsWith("what do you know about ")) {
            return tool("memory_search", Map.of("query", text.substring("what do you know about ".length())));
        }
        if (lower.startsWith("remind me ")) {
            return tool("reminder_create", Map.of("text", text.substring("remind me ".length()), "due_local", "2099-01-01T09:00"));
        }
        return new LlmResponse("(local model) You said: " + text, List.of(), "end_turn", Math.max(1, text.length() / 4), 10);
    }

    private static LlmResponse tool(String name, Map<String, Object> input) {
        return new LlmResponse("", List.of(new ToolUse("toolu_" + name, name, input)), "tool_use", 40, 15);
    }
}
