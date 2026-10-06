package com.weekend.assistant.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.Harness;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.domain.Folder;
import com.weekend.assistant.domain.TaskPriority;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FolderServiceTest {

    private final Harness h = new Harness(new ScriptedLlmProvider());

    @Test
    void createsFoldersWithValidatedNameAndIcon() {
        Folder f = h.folders.create("  Work  ", "work");
        assertThat(f.name()).isEqualTo("Work");
        assertThat(h.folders.create("Misc", null).icon()).isEqualTo("folder");
        assertThatThrownBy(() -> h.folders.create("", "work")).hasMessageContaining("name is required");
        assertThatThrownBy(() -> h.folders.create("x".repeat(61), "work")).hasMessageContaining("longer than 60");
        assertThatThrownBy(() -> h.folders.create("Bad", "rocket")).hasMessageContaining("icon must be one of");
        assertThat(h.audit.findAll()).extracting("action").contains("folder.create");
    }

    @Test
    void summaryCountsOpenTasksAndUpcomingRemindersWithPreview() {
        Folder f = h.folders.create("Work", "work");
        h.tasks.create("A", null, f.id(), null, TaskPriority.NORMAL, "owner");
        String done = h.tasks.create("B", null, f.id(), null, TaskPriority.NORMAL, "owner").id();
        h.tasks.complete(done);
        h.reminders.create("R1", h.clock.instant().plus(Duration.ofHours(1)), f.id());
        h.reminders.create("R2", h.clock.instant().plus(Duration.ofHours(2)), f.id()).ifPresent(r -> h.reminders.cancel(r.id()));
        h.tasks.create("Elsewhere", null, null, null, TaskPriority.NORMAL, "owner");

        FolderService.FolderSummary s = h.folders.summaries().get(0);
        assertThat(s.openTasks()).isEqualTo(1);
        assertThat(s.upcomingReminders()).isEqualTo(1);
        assertThat(s.preview()).containsExactly("A", "R1");
        assertThat(h.folders.contents(f.id())).get().satisfies(c -> {
            assertThat(c.tasks()).hasSize(2);
            assertThat(c.reminders()).hasSize(2);
        });
    }

    @Test
    void previewShowsAtMostFourItems() {
        Folder f = h.folders.create("Many", "star");
        for (int i = 0; i < 6; i++) {
            h.tasks.create("T" + i, null, f.id(), null, TaskPriority.NORMAL, "owner");
        }
        assertThat(h.folders.summaries().get(0).preview()).hasSize(4);
    }

    @Test
    void deletingAFolderKeepsItsItemsAsUnfiled() {
        Folder f = h.folders.create("Temp", "folder");
        String task = h.tasks.create("Keep me", null, f.id(), null, TaskPriority.NORMAL, "owner").id();
        String rem = h.reminders.create("Keep me too", h.clock.instant().plus(Duration.ofHours(1)), f.id()).orElseThrow().id();

        assertThat(h.folders.delete(f.id())).isTrue();
        assertThat(h.folders.delete(f.id())).isFalse();
        assertThat(h.taskRepo.findById(task)).get().extracting("folderId").isNull();
        assertThat(h.reminderRepo.findById(rem)).get().extracting("folderId").isNull();
    }

    @Test
    void updateRenamesAndLimitsFolderCount() {
        Folder f = h.folders.create("Old", "folder");
        assertThat(h.folders.update(f.id(), "New", "home")).get().satisfies(n -> {
            assertThat(n.name()).isEqualTo("New");
            assertThat(n.icon()).isEqualTo("home");
        });
        assertThat(h.folders.update("missing", "x", null)).isEmpty();
        for (int i = 1; i < FolderService.MAX_FOLDERS; i++) {
            h.folders.create("F" + i, "folder");
        }
        assertThatThrownBy(() -> h.folders.create("One too many", "folder")).hasMessageContaining("folder limit");
    }
}
