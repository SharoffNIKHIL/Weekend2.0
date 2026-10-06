package com.weekend.assistant.tools;

import com.weekend.assistant.domain.TaskStatus;
import com.weekend.assistant.workspace.TaskService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Lists the owner's open tasks (read-only). */
@Component
public class TaskListTool implements Tool {

    private final TaskService tasks;

    public TaskListTool(TaskService tasks) {
        this.tasks = tasks;
    }

    @Override
    public String name() {
        return "task_list";
    }

    @Override
    public String description() {
        return "List the owner's open tasks with due times and priority.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of(), List.of());
    }

    @Override
    public boolean writes() {
        return false;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        String list = tasks.all().stream().filter(t -> t.status() == TaskStatus.OPEN).limit(50)
                .map(t -> "- " + t.title() + " (" + t.priority().name().toLowerCase(java.util.Locale.ROOT)
                        + (t.dueAt() == null ? "" : ", due " + t.dueAt()) + ")")
                .collect(Collectors.joining("\n"));
        return ToolOutput.ok(list.isEmpty() ? "No open tasks." : list);
    }
}
