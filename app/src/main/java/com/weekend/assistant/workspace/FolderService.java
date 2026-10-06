package com.weekend.assistant.workspace;

import com.weekend.assistant.domain.Folder;
import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.domain.ReminderStatus;
import com.weekend.assistant.domain.Task;
import com.weekend.assistant.domain.TaskStatus;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.FolderRepository;
import com.weekend.assistant.port.ReminderRepository;
import com.weekend.assistant.port.TaskRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Home-screen folders that group tasks and reminders. Deleting a folder keeps its items (they become unfiled). */
@Service
public class FolderService {

    public static final Set<String> ICONS = Set.of("folder", "home", "work", "money", "health", "travel", "code", "cart", "star", "book");
    public static final int MAX_FOLDERS = 50;

    private final FolderRepository folders;
    private final TaskRepository tasks;
    private final ReminderRepository reminders;
    private final AuditLog audit;
    private final Clock clock;

    public FolderService(FolderRepository folders, TaskRepository tasks, ReminderRepository reminders, AuditLog audit, Clock clock) {
        this.folders = folders;
        this.tasks = tasks;
        this.reminders = reminders;
        this.audit = audit;
        this.clock = clock;
    }

    /** A folder plus what the home screen shows on its icon. */
    public record FolderSummary(Folder folder, long openTasks, long upcomingReminders, List<String> preview) {}

    /** A folder with its items. */
    public record FolderContents(Folder folder, List<Task> tasks, List<Reminder> reminders) {}

    public Folder create(String name, String icon) {
        if (folders.findAll().size() >= MAX_FOLDERS) {
            throw new IllegalArgumentException("folder limit reached (" + MAX_FOLDERS + ")");
        }
        Folder f = folders.save(new Folder(UUID.randomUUID().toString(), Inputs.required(name, "name", 60), icon(icon), clock.instant()));
        audit.append("owner", "folder.create", f.id());
        return f;
    }

    public Optional<Folder> update(String id, String name, String icon) {
        return folders.findById(id).map(f -> {
            audit.append("owner", "folder.update", id);
            return folders.save(f.renamed(name == null ? f.name() : Inputs.required(name, "name", 60), icon == null ? f.icon() : icon(icon)));
        });
    }

    public boolean delete(String id) {
        if (folders.findById(id).isEmpty()) {
            return false;
        }
        tasks.findAll().stream().filter(t -> id.equals(t.folderId())).forEach(t -> tasks.save(t.inFolder(null)));
        reminders.findAll().stream().filter(r -> id.equals(r.folderId())).forEach(r -> reminders.save(r.inFolder(null)));
        folders.deleteById(id);
        audit.append("owner", "folder.delete", id);
        return true;
    }

    public boolean exists(String id) {
        return id != null && folders.findById(id).isPresent();
    }

    public List<FolderSummary> summaries() {
        return folders.findAll().stream().map(this::summary).toList();
    }

    public Optional<FolderContents> contents(String id) {
        return folders.findById(id).map(f -> new FolderContents(f,
                tasks.findAll().stream().filter(t -> id.equals(t.folderId())).toList(),
                reminders.findAll().stream().filter(r -> id.equals(r.folderId())).toList()));
    }

    private FolderSummary summary(Folder f) {
        List<Task> open = tasks.findAll().stream().filter(t -> f.id().equals(t.folderId()) && t.status() == TaskStatus.OPEN).toList();
        List<Reminder> upcoming = reminders.findAll().stream()
                .filter(r -> f.id().equals(r.folderId()) && r.status() == ReminderStatus.SCHEDULED).toList();
        List<String> preview = Stream.concat(open.stream().map(Task::title), upcoming.stream().map(Reminder::text)).limit(4).toList();
        return new FolderSummary(f, open.size(), upcoming.size(), preview);
    }

    private static String icon(String icon) {
        String i = Objects.requireNonNullElse(icon, "folder");
        if (!ICONS.contains(i)) {
            throw new IllegalArgumentException("icon must be one of " + ICONS);
        }
        return i;
    }
}
