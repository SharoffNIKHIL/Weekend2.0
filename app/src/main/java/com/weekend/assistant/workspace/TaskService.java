package com.weekend.assistant.workspace;

import com.weekend.assistant.domain.Task;
import com.weekend.assistant.domain.TaskPriority;
import com.weekend.assistant.domain.TaskStatus;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.FolderRepository;
import com.weekend.assistant.port.TaskRepository;
import com.weekend.assistant.security.SecretFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** To-do items, optionally in a folder and with a due time. Secrets are redacted before saving (P4). */
@Service
public class TaskService {

    private final TaskRepository repo;
    private final FolderRepository folders;
    private final SecretFilter secrets;
    private final AuditLog audit;
    private final Clock clock;

    public TaskService(TaskRepository repo, FolderRepository folders, SecretFilter secrets, AuditLog audit, Clock clock) {
        this.repo = repo;
        this.folders = folders;
        this.secrets = secrets;
        this.audit = audit;
        this.clock = clock;
    }

    public Task create(String title, String notes, String folderId, Instant dueAt, TaskPriority priority, String actor) {
        String t = secrets.redact(Inputs.required(title, "title", 200));
        String n = notes == null ? null : secrets.redact(Inputs.optional(notes, "notes", 2000));
        Task task = repo.save(new Task(UUID.randomUUID().toString(), t, n, checkedFolder(folderId), dueAt,
                priority == null ? TaskPriority.NORMAL : priority, TaskStatus.OPEN, clock.instant(), null));
        audit.append(actor, "task.create", task.id());
        return task;
    }

    public List<Task> all() {
        return repo.findAll();
    }

    public List<Task> inFolder(String folderId) {
        return repo.findAll().stream().filter(t -> folderId.equals(t.folderId())).toList();
    }

    public long openCount() {
        return repo.findAll().stream().filter(t -> t.status() == TaskStatus.OPEN).count();
    }

    public Optional<Task> complete(String id) {
        return repo.findById(id).filter(t -> t.status() == TaskStatus.OPEN).map(t -> {
            audit.append("owner", "task.complete", id);
            return repo.save(t.completed(clock.instant()));
        });
    }

    public Optional<Task> reopen(String id) {
        return repo.findById(id).filter(t -> t.status() == TaskStatus.DONE).map(t -> {
            audit.append("owner", "task.reopen", id);
            return repo.save(t.reopened());
        });
    }

    public Optional<Task> move(String id, String folderId) {
        String folder = checkedFolder(folderId);
        return repo.findById(id).map(t -> {
            audit.append("owner", "task.move", id);
            return repo.save(t.inFolder(folder));
        });
    }

    public boolean delete(String id) {
        boolean removed = repo.deleteById(id);
        if (removed) {
            audit.append("owner", "task.delete", id);
        }
        return removed;
    }

    private String checkedFolder(String folderId) {
        if (folderId == null || folderId.isBlank()) {
            return null;
        }
        if (folders.findById(folderId).isEmpty()) {
            throw new IllegalArgumentException("unknown folder");
        }
        return folderId;
    }
}
