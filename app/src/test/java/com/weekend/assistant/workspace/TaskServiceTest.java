package com.weekend.assistant.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.Harness;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.domain.Task;
import com.weekend.assistant.domain.TaskPriority;
import com.weekend.assistant.domain.TaskStatus;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

    private final Harness h = new Harness(new ScriptedLlmProvider());

    @Test
    void createsWithDefaultsAndRedactsSecrets() {
        Task t = h.tasks.create("Rotate key AKIAABCDEFGHIJKLMNOP today", "  ", null, null, null, "owner");
        assertThat(t.priority()).isEqualTo(TaskPriority.NORMAL);
        assertThat(t.status()).isEqualTo(TaskStatus.OPEN);
        assertThat(t.title()).contains("[REDACTED]").doesNotContain("AKIA");
        assertThat(t.notes()).isNull();
    }

    @Test
    void validatesTitleAndFolder() {
        assertThatThrownBy(() -> h.tasks.create(" ", null, null, null, null, "owner")).hasMessageContaining("title is required");
        assertThatThrownBy(() -> h.tasks.create("x".repeat(201), null, null, null, null, "owner")).hasMessageContaining("longer than 200");
        assertThatThrownBy(() -> h.tasks.create("ok", "n".repeat(2001), null, null, null, "owner")).hasMessageContaining("notes");
        assertThatThrownBy(() -> h.tasks.create("ok", null, "no-such-folder", null, null, "owner")).hasMessageContaining("unknown folder");
    }

    @Test
    void completeAndReopenOnlyFromTheRightState() {
        Task t = h.tasks.create("Do it", null, null, null, TaskPriority.HIGH, "owner");
        assertThat(h.tasks.reopen(t.id())).isEmpty();
        assertThat(h.tasks.complete(t.id())).get().satisfies(d -> {
            assertThat(d.status()).isEqualTo(TaskStatus.DONE);
            assertThat(d.completedAt()).isEqualTo(h.clock.instant());
        });
        assertThat(h.tasks.complete(t.id())).isEmpty();
        assertThat(h.tasks.openCount()).isZero();
        assertThat(h.tasks.reopen(t.id())).get().extracting(Task::completedAt).isNull();
        assertThat(h.tasks.openCount()).isEqualTo(1);
    }

    @Test
    void moveAndDelete() {
        String folder = h.folders.create("Home", "home").id();
        Task t = h.tasks.create("Move me", null, null, null, null, "owner");
        assertThat(h.tasks.move(t.id(), folder)).get().extracting(Task::folderId).isEqualTo(folder);
        assertThat(h.tasks.inFolder(folder)).hasSize(1);
        assertThat(h.tasks.move(t.id(), null)).get().extracting(Task::folderId).isNull();
        assertThatThrownBy(() -> h.tasks.move(t.id(), "nope")).hasMessageContaining("unknown folder");
        assertThat(h.tasks.delete(t.id())).isTrue();
        assertThat(h.tasks.delete(t.id())).isFalse();
        assertThat(h.audit.findAll()).extracting("action").contains("task.create", "task.move", "task.delete");
    }
}
