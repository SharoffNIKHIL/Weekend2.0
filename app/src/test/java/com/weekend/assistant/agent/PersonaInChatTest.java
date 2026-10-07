package com.weekend.assistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.Harness;
import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.AgentMode;
import com.weekend.assistant.domain.AgentPersona;
import com.weekend.assistant.domain.ApprovalRange;
import com.weekend.assistant.domain.MemoryKind;
import com.weekend.assistant.domain.SearchRange;
import com.weekend.assistant.port.LlmProvider.ToolSpec;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How mode, sliders, search range and approval range change what the agent does. */
class PersonaInChatTest {

    private final AgentProfilesInChatTest.RecordingLlm llm = new AgentProfilesInChatTest.RecordingLlm();
    // ceilings as in application.yml (10 steps, 4096 tokens) so the efficiency budget is what limits
    private final WeekendProperties props = new WeekendProperties("Asia/Kolkata",
            new WeekendProperties.Llm("local", "", "global", "claude-haiku-4-5@20251001", "claude-sonnet-5", 4096,
                    new BigDecimal("1.00"), new BigDecimal("5.00"), new BigDecimal("2.00"), new BigDecimal("10.00")),
            new WeekendProperties.Agent(10, 8, 20, 1200, new BigDecimal("0.62")), TestFixtures.props().retention(),
            TestFixtures.props().security(), TestFixtures.props().agents(), null, null, null);
    private final Harness h = new Harness(llm, props);

    private void persona(AgentPersona p) {
        h.agents.updatePersona("weekend", p);
    }

    private List<String> toolNames(int request) {
        return llm.requests.get(request).tools().stream().map(ToolSpec::name).toList();
    }

    @Test
    void efficiencyPicksModelTokensAndTemperature() {
        persona(AgentPersona.preset(AgentMode.FUNNY));                          // efficiency 2
        ChatResult eco = h.agent.chat(null, "hello", false);
        assertThat(eco.model()).isEqualTo(props.llm().modelDefault());
        assertThat(llm.requests.get(0).maxTokens()).isEqualTo(1024);
        assertThat(llm.requests.get(0).temperature()).isEqualTo(AgentPersona.preset(AgentMode.FUNNY).temperature());

        persona(new AgentPersona(AgentMode.CUSTOM, 3, 9, 8, 5, SearchRange.MEMORY, ApprovalRange.WRITES_AND_EXTERNAL));
        ChatResult max = h.agent.chat(null, "hello", false);
        assertThat(max.model()).isEqualTo(props.llm().modelStrong());
        assertThat(llm.requests.get(1).maxTokens()).isEqualTo(4096);
        assertThat(llm.requests.get(1).system()).contains("Focus mode");
    }

    @Test
    void styleSettingsAreInTheSystemPromptAfterTheRules() {
        persona(AgentPersona.preset(AgentMode.DISCIPLINED));
        h.agent.chat(null, "hello", false);
        String system = llm.requests.get(0).system();
        assertThat(system.indexOf("You are Weekend")).isLessThan(system.indexOf("How the owner wants you to answer"));
        assertThat(system).contains("mode disciplined").contains("Humour 1/10: no jokes").contains("Truthfulness 10/10: facts only")
                .contains("Focus 9/10: answer in the fewest words");
    }

    @Test
    void searchRangeDecidesWhichLookupToolsExist() {
        h.web.enabled = true;
        persona(new AgentPersona(AgentMode.CUSTOM, 3, 9, 8, 3, SearchRange.OFF, ApprovalRange.WRITES_AND_EXTERNAL));
        h.agent.chat(null, "hello", false);
        assertThat(toolNames(0)).doesNotContain("memory_search", "task_list", "web_search").contains("math_evaluate");
        ChatResult blocked = h.agent.chat(null, "list my tasks", false);
        assertThat(blocked.reply()).contains("Tool not allowed for this agent: task_list");

        persona(AgentPersona.preset(AgentMode.FUNNY));                          // MEMORY
        h.agent.chat(null, "hello", false);
        assertThat(toolNames(llm.requests.size() - 1)).contains("memory_search", "task_list").doesNotContain("web_search");

        persona(AgentPersona.preset(AgentMode.WORK));                           // WEB
        h.agent.chat(null, "hello", false);
        assertThat(toolNames(llm.requests.size() - 1)).contains("web_search");
    }

    @Test
    void webSearchIsHiddenAndRefusedWhileWebIsOff() {
        persona(AgentPersona.preset(AgentMode.BROWSE));
        h.agent.chat(null, "hello", false);
        assertThat(toolNames(0)).doesNotContain("web_search");
        ChatResult r = h.agent.chat(null, "search compound interest", false);
        assertThat(r.pendingConfirmation()).isNull();
        assertThat(r.reply()).contains("Tool not available right now: web_search");
        assertThat(h.web.queries).isEmpty();
    }

    @Test
    void approvalRangesForWebSearch() {
        h.web.enabled = true;
        persona(AgentPersona.preset(AgentMode.WORK));                           // asks before external
        ChatResult asked = h.agent.chat(null, "search compound interest formula", false);
        assertThat(asked.pendingConfirmation()).isNotNull();
        assertThat(h.web.queries).isEmpty();
        h.agent.confirm(asked.pendingConfirmation().id(), true);
        assertThat(h.web.queries).containsExactly("compound interest formula|3");

        persona(AgentPersona.preset(AgentMode.BROWSE));                         // WRITES_ONLY + WIDE: runs at once, 6 results + summaries
        ChatResult ran = h.agent.chat(null, "search compound interest formula", false);
        assertThat(ran.pendingConfirmation()).isNull();
        assertThat(ran.toolsUsed()).containsExactly("web_search");
        assertThat(ran.reply()).contains("Summary of Compound interest");
        assertThat(h.web.queries).last().isEqualTo("compound interest formula|6");

        persona(AgentPersona.preset(AgentMode.DISCIPLINED));                    // ALL: even math asks
        assertThat(h.agent.chat(null, "calculate 2+2", false).pendingConfirmation()).isNotNull();
    }

    @Test
    void writesAndOtherAgentsAlwaysAskEvenInBrowseMode() {
        persona(AgentPersona.preset(AgentMode.BROWSE));
        assertThat(h.agent.chat(null, "add task call the bank", false).pendingConfirmation()).isNotNull();
        assertThat(AgentService.needsApproval(new com.weekend.assistant.tools.AgentDelegateTool(h.agents, h.remote, h.inbox, h.secrets),
                false, ApprovalRange.WRITES_ONLY)).isTrue();
    }

    @Test
    void mathRunsLocallyWithoutApprovalInWorkMode() {
        ChatResult r = h.agent.chat(null, "calculate 2^10 + 25!", false);
        assertThat(r.toolsUsed()).containsExactly("math_evaluate");
        assertThat(r.reply()).contains("15511210043330985984001024");
        assertThat(h.agent.chat(null, "solve x^2 - 5x + 6 = 0", false).reply()).contains("x = 2, 3");
        assertThat(h.agent.chat(null, "stats 2 4 4 4 5 5 7 9", false).reply()).contains("mean: 5");
    }

    @Test
    void pinnedProfileMemoriesAreAlwaysInContext() {
        h.memories.pin(h.memories.remember("I have been a DevOps engineer since July 2018", MemoryKind.FACT, null).orElseThrow().id(), true);
        h.memories.remember("unpinned note about gardening", MemoryKind.FACT, null);
        h.agent.chat(null, "what should I cook tonight?", false);
        assertThat(llm.requests.get(0).system()).contains("DevOps engineer since July 2018").doesNotContain("gardening");
    }

    @Test
    void personaCannotBeSetOnRemoteAgentsOrUnknownIds() {
        Harness withRemote = new Harness(llm, TestFixtures.props(TestFixtures.agents(List.of("agents.example.com"), List.of())));
        String remote = withRemote.agents.connectRemote("R", null, "https://agents.example.com").agent().id();
        assertThat(withRemote.agents.updatePersona(remote, AgentPersona.DEFAULT)).isEmpty();
        assertThat(withRemote.agents.updatePersona("nope", AgentPersona.DEFAULT)).isEmpty();
        assertThatThrownBy(() -> withRemote.agents.updatePersona("weekend", null)).hasMessageContaining("persona");
        assertThat(withRemote.audit.findAll()).extracting("action").doesNotContain("agent.persona");
        assertThat(h.agents.updatePersona("weekend", AgentPersona.preset(AgentMode.FUNNY))).isPresent();
        assertThat(h.audit.findAll()).extracting("action").contains("agent.persona");
    }
}
