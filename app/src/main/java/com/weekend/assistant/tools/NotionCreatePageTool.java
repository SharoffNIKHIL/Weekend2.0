package com.weekend.assistant.tools;

import com.weekend.assistant.port.NotionClient;
import com.weekend.assistant.security.SecretFilter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Saves a note as a new Notion page. Writes and 🔓 external: always waits for the owner's yes. Secrets are refused. */
@Component
public class NotionCreatePageTool implements Tool {

    static final int MAX_BODY = 20_000;

    private final NotionClient notion;
    private final SecretFilter secrets;

    public NotionCreatePageTool(NotionClient notion, SecretFilter secrets) {
        this.notion = notion;
        this.secrets = secrets;
    }

    @Override
    public String name() {
        return "notion_create_page";
    }

    @Override
    public String description() {
        return "Save a note to Notion as a new page (title + text; blank lines separate paragraphs). The owner approves first.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of("title", Schemas.string("Page title"), "text", Schemas.string("Note text")), List.of("title", "text"));
    }

    @Override
    public boolean writes() {
        return true;
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
        String title = Schemas.requireString(input, "title");
        String text = Schemas.requireString(input, "text");
        if (title.length() > 200 || text.length() > MAX_BODY) {
            return ToolOutput.error("Title must be ≤ 200 and text ≤ " + MAX_BODY + " characters.");
        }
        if (secrets.containsSecret(title) || secrets.containsSecret(text)) {
            return ToolOutput.error("The note looks like it contains a secret; not saved to Notion.");
        }
        try {
            NotionClient.Page p = notion.createPage(title, text);
            return ToolOutput.ok("Saved to Notion: " + p.title() + " — " + p.url());
        } catch (NotionClient.NotionException e) {
            return ToolOutput.error("Notion: " + e.getMessage());
        }
    }
}
