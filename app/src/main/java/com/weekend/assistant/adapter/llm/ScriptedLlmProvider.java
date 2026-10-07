package com.weekend.assistant.adapter.llm;

import com.weekend.assistant.port.LlmProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic, offline LLM for local runs and tests (weekend.llm.provider=local). No data leaves
 * the machine. It recognises a few intents so the agent loop and tools can be exercised end to end:
 * "time"/"date" → current_time, "what do you know about X" → memory_search, "remind me" → reminder_create,
 * "add task X" → task_create, "list my tasks" → task_list, "delegate to <agent_id>: X" → agent_delegate,
 * "calculate X" → math_evaluate, "solve X" → math_solve, "stats X" → math_stats, "search X" → web_search,
 * "draw X" → a 4K SVG artwork, attached images → a pixel-level description (format, size, colour, brightness),
 * "notes X" → notion_search, "save note T: X" → notion_create_page, "my payments" → payment_list.
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
        if (last instanceof UserText u && !u.images().isEmpty()) {
            StringBuilder sb = new StringBuilder("(local model) I looked at ").append(u.images().size())
                    .append(u.images().size() == 1 ? " image" : " images").append(":\n");
            u.images().forEach(img -> sb.append("- ").append(LocalVision.describe(img)).append('\n'));
            return new LlmResponse(sb.toString().strip(), List.of(), "end_turn", 400, 60);
        }
        for (String verb : new String[] {"draw ", "paint ", "create art ", "make art "}) {
            if (lower.startsWith(verb)) {
                return new LlmResponse("```svg\n" + LocalArt.svg(text) + "\n```\nAn original 4K landscape for \"" + text.substring(verb.length())
                        + "\": layered ranges, glow and a reflection. Export it as a 4K PNG or SVG.", List.of(), "end_turn", 80, 900);
            }
        }
        if (lower.startsWith("my payments")) {
            return tool("payment_list", Map.of());
        }
        if (lower.startsWith("notes ")) {
            return tool("notion_search", Map.of("query", text.substring(6)));
        }
        if (lower.startsWith("save note ") && text.indexOf(':') > 10) {
            int c = text.indexOf(':');
            return tool("notion_create_page", Map.of("title", text.substring(10, c).strip(), "text", text.substring(c + 1).strip()));
        }
        for (String[] intent : new String[][] {{"calculate ", "math_evaluate", "expression"}, {"solve ", "math_solve", "equation"},
                {"stats ", "math_stats", "numbers"}, {"search ", "web_search", "query"}}) {
            if (lower.startsWith(intent[0])) {
                return tool(intent[1], Map.of(intent[2], text.substring(intent[0].length())));
            }
        }
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
