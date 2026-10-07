package com.weekend.assistant.tools;

import com.weekend.assistant.port.NotionClient;
import com.weekend.assistant.security.SecretFilter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Searches the owner's Notion pages by title. 🔓 External (the query goes to Notion). Read-only. */
@Component
public class NotionSearchTool implements Tool {

    private final NotionClient notion;
    private final SecretFilter secrets;

    public NotionSearchTool(NotionClient notion, SecretFilter secrets) {
        this.notion = notion;
        this.secrets = secrets;
    }

    @Override
    public String name() {
        return "notion_search";
    }

    @Override
    public String description() {
        return "Search the owner's Notion pages (only pages shared with the Weekend integration). Returns titles and links.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of("query", Schemas.string("Words in the page title")), List.of("query"));
    }

    @Override
    public boolean writes() {
        return false;
    }

    @Override
    public boolean external() {
        return true;
    }

    @Override
    public boolean available() {
        return notion.enabled();
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        String q = secrets.redact(Schemas.requireString(input, "query"));
        try {
            List<NotionClient.Page> pages = notion.search(q, 8);
            return ToolOutput.ok(pages.isEmpty() ? "No Notion pages match: " + q
                    : pages.stream().map(p -> "- " + p.title() + " — " + p.url()).collect(Collectors.joining("\n")));
        } catch (NotionClient.NotionException e) {
            return ToolOutput.error("Notion: " + e.getMessage());
        }
    }
}
