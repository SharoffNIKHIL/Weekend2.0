package com.weekend.assistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.Harness;
import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.domain.ReminderStatus;
import com.weekend.assistant.domain.Role;
import com.weekend.assistant.port.LlmProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

    @Test
    void plainChatSavesBothTurnsAndCost() {
        Harness h = new Harness(new ScriptedLlmProvider());
        ChatResult r = h.agent.chat(null, "hello there", false);
        assertThat(r.reply()).contains("hello there");
        assertThat(r.model()).isEqualTo("claude-haiku-4-5@20251001");
        assertThat(h.messages.findAll()).extracting(m -> m.role()).containsExactly(Role.USER, Role.ASSISTANT);
        assertThat(r.costUsd()).isPositive();
        assertThat(h.agent.chat(r.conversationId(), "again", false).conversationId()).isEqualTo(r.conversationId());
    }

    @Test
    void readToolRunsAndResultIsWrappedAsData() {
        List<LlmProvider.LlmRequest> seen = new ArrayList<>();
        ScriptedLlmProvider scripted = new ScriptedLlmProvider();
        Harness h = new Harness(req -> { seen.add(req); return scripted.complete(req); });
        ChatResult r = h.agent.chat(null, "what time is it?", false);
        assertThat(r.toolsUsed()).containsExactly("current_time");
        assertThat(r.reply()).contains("2026").contains("IST");
        LlmProvider.ToolResults results = (LlmProvider.ToolResults) seen.get(1).turns().get(seen.get(1).turns().size() - 1);
        assertThat(results.results().get(0).content()).startsWith("<data>");
        assertThat(h.toolCalls.findAll()).hasSize(1);
    }

    @Test
    void writeToolPausesForConfirmationThenRuns() {
        Harness h = new Harness(new ScriptedLlmProvider());
        ChatResult r = h.agent.chat(null, "remind me to rotate the key", false);
        assertThat(r.pendingConfirmation()).isNotNull();
        assertThat(h.reminders.all()).isEmpty(); // nothing happens before the owner says yes

        assertThat(h.agent.confirm(r.pendingConfirmation().id(), true)).get().asString().contains("Reminder");
        assertThat(h.reminders.all()).singleElement().extracting(rem -> rem.status()).isEqualTo(ReminderStatus.SCHEDULED);
        assertThat(h.scheduler.isScheduled(h.reminders.all().get(0).id())).isTrue();
        assertThat(h.agent.confirm(r.pendingConfirmation().id(), true)).isEmpty(); // single use
    }

    @Test
    void declinedWriteChangesNothing() {
        Harness h = new Harness(new ScriptedLlmProvider());
        ChatResult r = h.agent.chat(null, "remind me to call mum", false);
        assertThat(h.agent.confirm(r.pendingConfirmation().id(), false)).get().asString().contains("Cancelled");
        assertThat(h.reminders.all()).isEmpty();
    }

    @Test
    void stopsAfterMaxToolStepsAndHandlesUnknownTools() {
        int[] calls = {0};
        Harness h = new Harness(req -> {
            calls[0]++;
            return new LlmProvider.LlmResponse("", List.of(new LlmProvider.ToolUse("t" + calls[0], "no_such_tool", Map.of())), "tool_use", 10, 5);
        });
        ChatResult r = h.agent.chat(null, "loop forever", false);
        assertThat(calls[0]).isEqualTo(h.props.agent().maxToolSteps() + 1);
        assertThat(r.reply()).contains("maximum number of tool steps");
    }

    @Test
    void explicitMemoryIsSavedAndSecretsAreRedactedInHistory() {
        Harness h = new Harness(new ScriptedLlmProvider());
        assertThat(h.agent.chat(null, "Remember that I prefer tea", false).memorySaved()).isTrue();
        h.agent.chat(null, "my api key: sk-ant-abcdefghijklmnop1234", false);
        assertThat(h.messages.findAll()).noneMatch(m -> m.content().contains("sk-ant-abcdefghijklmnop1234"));
        assertThat(h.memories.all()).hasSize(1);
    }

    @Test
    void dailyCostCapBlocksFurtherCalls() {
        Harness h = new Harness(new ScriptedLlmProvider(), TestFixtures.props(new BigDecimal("0.0000001")));
        h.agent.chat(null, "first", false);
        assertThatThrownBy(() -> h.agent.chat(null, "second", false)).isInstanceOf(CostCapExceededException.class);
    }
}
