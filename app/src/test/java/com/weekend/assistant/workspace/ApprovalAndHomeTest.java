package com.weekend.assistant.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekend.assistant.Harness;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.domain.NotificationKind;
import com.weekend.assistant.domain.TaskPriority;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ApprovalAndHomeTest {

    private final Harness h = new Harness(new ScriptedLlmProvider());

    @Test
    void approvalsCombineToolCallsAndPayments() {
        h.agent.chat(null, "add task file taxes", false);
        h.payments.request("Rent", new BigDecimal("100"), null, null, "owner");

        assertThat(h.approvals.pending()).extracting(ApprovalService.Approval::type)
                .containsExactlyInAnyOrder(ApprovalService.Type.TOOL, ApprovalService.Type.PAYMENT);
        assertThat(h.notifications.all()).extracting("kind").containsOnly(NotificationKind.APPROVAL);

        ApprovalService.Approval tool = h.approvals.pending().stream().filter(a -> a.type() == ApprovalService.Type.TOOL).findFirst().orElseThrow();
        assertThat(tool.title()).isEqualTo("Add a task");
        assertThat(h.approvals.decide(ApprovalService.Type.TOOL, tool.id(), true)).get().asString().contains("file taxes");
        assertThat(h.tasks.all()).singleElement().extracting("title").isEqualTo("file taxes");

        ApprovalService.Approval pay = h.approvals.pending().get(0);
        assertThat(h.approvals.decide(ApprovalService.Type.PAYMENT, pay.id(), true)).get().asString().contains("does not pay");
        assertThat(h.approvals.pending()).isEmpty();
        assertThat(h.approvals.decide(ApprovalService.Type.PAYMENT, pay.id(), true)).isEmpty();
        assertThat(h.approvals.decide(ApprovalService.Type.TOOL, "missing", true)).isEmpty();
    }

    @Test
    void declinedToolApprovalChangesNothing() {
        h.agent.chat(null, "add task nothing", false);
        String id = h.approvals.pending().get(0).id();
        assertThat(h.approvals.decide(ApprovalService.Type.TOOL, id, false)).get().asString().contains("Nothing was changed");
        assertThat(h.tasks.all()).isEmpty();
    }

    @Test
    void homeCountsEverythingAndListsUpNextInDueOrder() {
        String work = h.folders.create("Work", "work").id();
        h.tasks.create("Later task", null, work, h.clock.instant().plus(Duration.ofDays(3)), TaskPriority.NORMAL, "owner");
        h.tasks.create("No due date", null, null, null, TaskPriority.NORMAL, "owner");
        h.reminders.create("Soon reminder", h.clock.instant().plus(Duration.ofHours(1)), work);
        for (int i = 0; i < 6; i++) {
            h.reminders.create("R" + i, h.clock.instant().plus(Duration.ofDays(10 + i)));
        }
        h.payments.request("Rent", BigDecimal.TEN, null, null, "owner");
        h.inbox.receive("system", "Weekend", "Hi", "Body");

        HomeService.Home home = h.home.home();
        assertThat(home.counts().reminders()).isEqualTo(7);
        assertThat(home.counts().tasks()).isEqualTo(2);
        assertThat(home.counts().approvals()).isEqualTo(1);
        assertThat(home.counts().payments()).isEqualTo(1);
        assertThat(home.counts().messages()).isEqualTo(1);
        assertThat(home.counts().notifications()).isEqualTo(2);
        assertThat(home.folders()).hasSize(1);
        assertThat(home.upNext()).hasSize(5).first().extracting(HomeService.UpNext::title).isEqualTo("Soon reminder");
        assertThat(home.upNext().get(1).type()).isEqualTo("task");
    }
}
