package com.weekend.assistant.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.Harness;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.domain.ReminderStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReminderFolderTest {

    private final Harness h = new Harness(new ScriptedLlmProvider());

    @Test
    void createInFolderMoveAndValidate() {
        String folder = h.folders.create("Money", "money").id();
        Instant due = h.clock.instant().plus(Duration.ofDays(1));
        Reminder r = h.reminders.create("Pay bill", due, folder).orElseThrow();
        assertThat(r.folderId()).isEqualTo(folder);
        assertThat(h.reminders.move(r.id(), null)).get().extracting(Reminder::folderId).isNull();
        assertThat(h.reminders.move("missing", folder)).isEmpty();
        assertThatThrownBy(() -> h.reminders.create("x", due, "nope")).hasMessageContaining("unknown folder");
        assertThatThrownBy(() -> h.reminders.create(" ", due, null)).hasMessageContaining("text is required");
        assertThat(h.reminders.create("past", h.clock.instant().minusSeconds(1), null)).isEmpty();
        assertThat(h.reminders.create("no time", null, null)).isEmpty();
    }

    @Test
    void statusChangesKeepTheFolderAndCountOnlyScheduled() {
        String folder = h.folders.create("Work", "work").id();
        Reminder r = h.reminders.create("A", h.clock.instant().plus(Duration.ofHours(1)), folder).orElseThrow();
        h.reminders.create("B", h.clock.instant().plus(Duration.ofHours(2)), folder);
        assertThat(h.reminders.upcomingCount()).isEqualTo(2);
        assertThat(h.reminders.cancel(r.id())).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo(ReminderStatus.CANCELLED);
            assertThat(c.folderId()).isEqualTo(folder);
        });
        assertThat(h.reminders.upcomingCount()).isEqualTo(1);
    }
}
