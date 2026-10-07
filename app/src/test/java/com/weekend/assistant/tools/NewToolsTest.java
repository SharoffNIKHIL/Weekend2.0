package com.weekend.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.Harness;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.domain.Task;
import com.weekend.assistant.domain.TaskPriority;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NewToolsTest {

    private final Harness h = new Harness(new ScriptedLlmProvider());
    private final ToolContext ctx = new ToolContext("c1", "m1");

    @Test
    void taskCreateParsesDueTimeInOwnerZoneAndPriority() {
        TaskCreateTool tool = new TaskCreateTool(h.tasks, h.props);
        assertThat(tool.writes()).isTrue();
        assertThat(tool.execute(Map.of("title", "Pay rent", "due_local", "2026-10-15T09:00", "priority", "high"), ctx).isError()).isFalse();
        Task t = h.tasks.all().get(0);
        assertThat(t.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(t.dueAt()).isEqualTo(LocalDateTime.parse("2026-10-15T09:00").atZone(h.props.zone()).toInstant());
        assertThat(tool.execute(Map.of("title", "x", "due_local", "tomorrow"), ctx).isError()).isTrue();
        assertThat(tool.execute(Map.of("title", "x", "priority", "urgent"), ctx).isError()).isTrue();
        assertThatThrownBy(() -> tool.execute(Map.of(), ctx)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void taskListIsReadOnlyAndShowsOnlyOpenTasks() {
        TaskListTool tool = new TaskListTool(h.tasks);
        assertThat(tool.writes()).isFalse();
        assertThat(tool.execute(Map.of(), ctx).content()).isEqualTo("No open tasks.");
        h.tasks.create("Open one", null, null, null, TaskPriority.LOW, "owner");
        h.tasks.complete(h.tasks.create("Done one", null, null, null, null, "owner").id());
        assertThat(tool.execute(Map.of(), ctx).content()).contains("Open one (low)").doesNotContain("Done one");
    }

    @Test
    void paymentListIsReadOnly() {
        PaymentListTool tool = new PaymentListTool(h.payments);
        assertThat(tool.writes()).isFalse();
        assertThat(tool.external()).isFalse();
        assertThat(tool.execute(Map.of(), ctx).content()).isEqualTo("No payments tracked.");
        h.payments.request("Rent", new java.math.BigDecimal("25000"), null, null, "owner");
        assertThat(tool.execute(Map.of(), ctx).content()).contains("Rent: INR 25000.00 (pending_approval)");
    }

    @Test
    void notionToolsAreExternalHiddenWhenOffAndRefuseSecrets() {
        NotionSearchTool search = new NotionSearchTool(h.notion, h.secrets);
        NotionCreatePageTool create = new NotionCreatePageTool(h.notion, h.secrets);
        assertThat(search.external()).isTrue();
        assertThat(search.writes()).isFalse();
        assertThat(create.writes()).isTrue();
        assertThat(search.available()).isFalse();
        h.notion.enabled = true;
        assertThat(create.available()).isTrue();
        assertThat(create.execute(Map.of("title", "Keys", "text", "password: hunter2"), ctx).content()).contains("secret");
        assertThat(create.execute(Map.of("title", "x".repeat(201), "text", "t"), ctx).isError()).isTrue();
        assertThat(h.notion.calls).isEmpty();
        assertThat(search.execute(Map.of("query", "review"), ctx).content()).contains("Weekly review — https://www.notion.so/p1");
    }
}
