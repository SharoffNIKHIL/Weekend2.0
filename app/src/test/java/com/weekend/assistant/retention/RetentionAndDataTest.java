package com.weekend.assistant.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekend.assistant.Harness;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetentionAndDataTest {

    @Test
    void retentionDeletesOldMessagesAndToolCalls() {
        Harness h = new Harness(new ScriptedLlmProvider());
        h.agent.chat(null, "what time is it?", false);
        h.clock.advance(Duration.ofDays(366));
        h.agent.chat(null, "new message", false);
        RetentionService.RetentionReport report = h.retention.run();
        assertThat(report.messagesDeleted()).isEqualTo(2);
        assertThat(report.toolCallsDeleted()).isEqualTo(1);
        assertThat(h.messages.findAll()).hasSize(2);
    }

    @Test
    void exportAndDeleteAllRespectConfirmationPhrase() {
        Harness h = new Harness(new ScriptedLlmProvider());
        DataService data = new DataService(h.conversations, h.messages, h.memoryRepo, h.reminderRepo, h.toolCalls, h.audit, h.clock);
        h.agent.chat(null, "Remember that I like dosa", false);

        DataService.Export e = data.export("owner");
        assertThat(e.messages()).hasSize(2);
        assertThat(e.memories()).hasSize(1);

        assertThat(data.deleteAll("delete")).isFalse();
        assertThat(h.messages.findAll()).isNotEmpty();
        assertThat(data.deleteAll(DataService.DELETE_CONFIRMATION)).isTrue();
        assertThat(h.messages.findAll()).isEmpty();
        assertThat(h.memoryRepo.findAll()).isEmpty();
        assertThat(h.audit.findAll()).extracting("action").contains("data.delete_all"); // audit kept (P8)
        assertThat(h.audit.verify()).isTrue();
    }
}
