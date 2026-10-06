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
    void agentDelegateAlwaysNeedsConfirmation() {
        AgentDelegateTool tool = new AgentDelegateTool(h.agents, h.remote, h.inbox, h.secrets);
        assertThat(tool.writes()).isTrue();
        assertThat(tool.execute(Map.of("agent_id", "weekend", "message", "hi"), ctx).isError()).isTrue();
        assertThat(tool.execute(Map.of("agent_id", "nope", "message", "hi"), ctx).content()).contains("No connected remote agent");
    }
}
