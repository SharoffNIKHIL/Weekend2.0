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
        DataService data = h.data;
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

    @Test
    void exportAndDeleteAllCoverTheWorkspaceAndOwnerCreatedAgents() {
        Harness h = new Harness(new ScriptedLlmProvider(), com.weekend.assistant.TestFixtures.props(
                com.weekend.assistant.TestFixtures.agents(java.util.List.of("agents.example.com"), java.util.List.of())));
        String folder = h.folders.create("Work", "work").id();
        h.tasks.create("Task", null, folder, null, null, "owner");
        h.payments.request("Rent", java.math.BigDecimal.TEN, null, null, "owner");
        h.inbox.receive("system", "Weekend", "Hi", "Body");
        h.agents.createCustom("Mine", null, "My own instructions", null);
        var remote = h.agents.connectRemote("Helper", null, "https://agents.example.com");

        DataService.Export e = h.data.export("owner");
        assertThat(e.folders()).hasSize(1);
        assertThat(e.tasks()).hasSize(1);
        assertThat(e.payments()).hasSize(1);
        assertThat(e.inboxMessages()).hasSize(1);
        assertThat(e.notifications()).hasSize(2);
        assertThat(e.agents()).hasSize(3).extracting(DataService.AgentExport::instructions).contains("My own instructions");
        assertThat(e.toString()).doesNotContain(remote.agent().inboundTokenHash()).doesNotContain(remote.inboundToken());

        assertThat(h.data.deleteAll(DataService.DELETE_CONFIRMATION)).isTrue();
        assertThat(h.folderRepo.findAll()).isEmpty();
        assertThat(h.taskRepo.findAll()).isEmpty();
        assertThat(h.paymentRepo.findAll()).isEmpty();
        assertThat(h.inboxRepo.findAll()).isEmpty();
        assertThat(h.notificationRepo.findAll()).isEmpty();
        assertThat(h.agents.all()).extracting("id").containsExactly("weekend");
        assertThat(h.agents.authenticateInbound(remote.inboundToken())).isEmpty();   // connection revoked
    }
}
