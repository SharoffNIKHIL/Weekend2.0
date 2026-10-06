package com.weekend.assistant.tools;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.Task;
import com.weekend.assistant.domain.TaskPriority;
import com.weekend.assistant.workspace.TaskService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Adds a task. Writes = true, so the agent asks the owner first. */
@Component
public class TaskCreateTool implements Tool {

    private final TaskService tasks;
    private final WeekendProperties props;

    public TaskCreateTool(TaskService tasks, WeekendProperties props) {
        this.tasks = tasks;
        this.props = props;
    }

    @Override
    public String name() {
        return "task_create";
    }

    @Override
    public String description() {
        return "Add a task to the owner's list. Optional due_local (ISO local date-time, owner's time zone) and priority (low|normal|high).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of(
                        "title", Schemas.string("Short task title"),
                        "due_local", Schemas.string("Optional ISO-8601 local date-time, e.g. 2026-10-15T09:00"),
                        "priority", Schemas.string("Optional: low, normal or high")),
                List.of("title"));
    }

    @Override
    public boolean writes() {
        return true;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        String title = Schemas.requireString(input, "title");
        Instant due = null;
        if (input.get("due_local") instanceof String s && !s.isBlank()) {
            try {
                due = LocalDateTime.parse(s.trim()).atZone(props.zone()).toInstant();
            } catch (DateTimeParseException e) {
                return ToolOutput.error("due_local must look like 2026-10-15T09:00");
            }
        }
        TaskPriority priority = TaskPriority.NORMAL;
        if (input.get("priority") instanceof String p && !p.isBlank()) {
            try {
                priority = TaskPriority.valueOf(p.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ToolOutput.error("priority must be low, normal or high");
            }
        }
        Task t = tasks.create(title, null, null, due, priority, "agent");
        return ToolOutput.ok("Task " + t.id() + " added: " + t.title());
    }
}
