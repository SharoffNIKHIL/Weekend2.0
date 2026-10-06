package com.weekend.assistant.web;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.Task;
import com.weekend.assistant.domain.TaskPriority;
import com.weekend.assistant.workspace.TaskService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The owner's tasks. Created here directly by the owner, or by the agent after confirmation (task_create). */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService tasks;
    private final WeekendProperties props;

    public TaskController(TaskService tasks, WeekendProperties props) {
        this.tasks = tasks;
        this.props = props;
    }

    /** dueLocal: "2026-10-15T09:00" in the owner's time zone (optional). */
    public record TaskRequest(String title, String notes, String folderId, String dueLocal, TaskPriority priority) {}

    public record MoveRequest(String folderId) {}

    @GetMapping
    public List<Task> list(@RequestParam(required = false) String folderId) {
        return folderId == null ? tasks.all() : tasks.inFolder(folderId);
    }

    @PostMapping
    public ResponseEntity<Task> create(@RequestBody TaskRequest req) {
        Task t = tasks.create(req.title(), req.notes(), req.folderId(), Dates.localDateTime(req.dueLocal(), props.zone()),
                req.priority(), "owner");
        return ResponseEntity.status(HttpStatus.CREATED).body(t);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Task> complete(@PathVariable String id) {
        return ResponseEntity.of(tasks.complete(id));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<Task> reopen(@PathVariable String id) {
        return ResponseEntity.of(tasks.reopen(id));
    }

    @PutMapping("/{id}/folder")
    public ResponseEntity<Task> move(@PathVariable String id, @RequestBody MoveRequest req) {
        return ResponseEntity.of(tasks.move(id, req.folderId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return tasks.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
