package com.weekend.assistant.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.Harness;
import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.AgentConditions;
import com.weekend.assistant.domain.AgentKind;
import com.weekend.assistant.domain.AgentProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentDirectoryTest {

    @TempDir Path dir;

    private Harness harness(List<String> hosts, List<WeekendProperties.CustomAgent> custom) {
        return new Harness(new ScriptedLlmProvider(), TestFixtures.props(TestFixtures.agents(hosts, custom)));
    }

    private static WeekendProperties.CustomAgent agent(String id, Path file) {
        return new WeekendProperties.CustomAgent(id, "Project agent", "desc", file == null ? null : file.toString(),
                List.of("current_time"), true, false, 99);
    }

    @Test
    void builtInWeekendIsAlwaysThereAndIsTheDefault() {
        Harness h = harness(List.of(), List.of());
        assertThat(h.agents.all()).singleElement().satisfies(a -> {
            assertThat(a.id()).isEqualTo(AgentDirectory.DEFAULT_ID);
            assertThat(a.kind()).isEqualTo(AgentKind.BUILTIN);
            assertThat(a.editable()).isFalse();
        });
        assertThat(h.agents.find(null)).get().extracting(AgentProfile::id).isEqualTo("weekend");
        assertThat(h.agents.find(" ")).isPresent();
        assertThat(h.agents.delete("weekend")).isFalse();
        assertThat(h.agents.remoteAllowed()).isFalse();
    }

    @Test
    void loadsAConfiguredAgentFromItsInstructionsFile() throws IOException {
        Path file = Files.writeString(dir.resolve("CLAUDE.md"), "# RULES\nIf the owner pastes a secret: do not repeat it.\n");
        Harness h = harness(List.of(), List.of(agent("project-agent", file)));
        AgentProfile a = h.agents.find("project-agent").orElseThrow();
        assertThat(a.kind()).isEqualTo(AgentKind.CUSTOM);
        assertThat(a.instructions()).contains("pastes a secret");     // keyword heuristic does not block instructions
        assertThat(a.instructionsSource()).isEqualTo("file: CLAUDE.md");
        assertThat(a.conditions().allowedTools()).containsExactly("current_time");
        assertThat(a.conditions().confirmAllTools()).isTrue();
        assertThat(a.conditions().maxToolSteps()).isEqualTo(10);       // capped
        assertThat(a.editable()).isFalse();
    }

    @Test
    void skipsConfiguredAgentsWithoutAUsableFile() throws IOException {
        Path withKey = Files.writeString(dir.resolve("bad.md"), "use AKIAABCDEFGHIJKLMNOP");
        Path huge = Files.writeString(dir.resolve("huge.md"), "x".repeat(AgentDirectory.MAX_INSTRUCTIONS + 1));
        Harness h = harness(List.of(), List.of(agent("none", null), agent("missing", dir.resolve("nope.md")),
                agent("key", withKey), agent("huge", huge)));
        assertThat(h.agents.all()).extracting(AgentProfile::id).containsExactly("weekend");
    }

    @Test
    void ownerCreatedCustomAgents() {
        Harness h = harness(List.of(), List.of());
        AgentProfile a = h.agents.createCustom("Writer", null, "Write short answers.",
                new AgentConditions(List.of("memory_search"), false, true, 3));
        assertThat(a.id()).startsWith("custom-");
        assertThat(a.editable()).isTrue();
        assertThatThrownBy(() -> h.agents.createCustom("Bad", null, "key sk-abcdefghijklmnopqrstu", null))
                .hasMessageContaining("must not contain");
        assertThatThrownBy(() -> h.agents.createCustom("", null, "x", null)).hasMessageContaining("name");
        assertThatThrownBy(() -> h.agents.createCustom("x", null, " ", null)).hasMessageContaining("instructions");

        assertThat(h.agents.updateConditions(a.id(), new AgentConditions(null, true, false, 50)))
                .get().extracting(p -> p.conditions().maxToolSteps()).isEqualTo(10);
        assertThat(h.agents.updateConditions("weekend", AgentConditions.DEFAULT)).isEmpty(); // built-in is fixed
        assertThat(h.agents.delete(a.id())).isTrue();
    }

    @Test
    void remoteAgentsNeedAnAllowListedHostAndGetAOneTimeInboundToken() {
        Harness none = harness(List.of(), List.of());
        assertThatThrownBy(() -> none.agents.connectRemote("X", null, "https://agents.example.com"))
                .hasMessageContaining("allowed-hosts");

        Harness h = harness(List.of("agents.example.com"), List.of());
        AgentDirectory.Connection c = h.agents.connectRemote("Helper", "d", "https://agents.example.com/a/");
        assertThat(c.agent().endpoint()).isEqualTo("https://agents.example.com/a");
        assertThat(c.inboundToken()).startsWith("wkd_").hasSizeGreaterThan(40);
        assertThat(c.agent().inboundTokenHash()).isEqualTo(AgentDirectory.sha256(c.inboundToken())).doesNotContain(c.inboundToken());
        assertThat(c.agent().conditions().allowedTools()).isEmpty();

        assertThat(h.agents.authenticateInbound(c.inboundToken())).get().extracting(AgentProfile::id).isEqualTo(c.agent().id());
        assertThat(h.agents.authenticateInbound(c.inboundToken() + "x")).isEmpty();
        assertThat(h.agents.authenticateInbound(null)).isEmpty();
        assertThat(h.agents.authenticateInbound("short")).isEmpty();
        assertThat(h.agents.updateConditions(c.agent().id(), AgentConditions.DEFAULT)).isEmpty(); // remote: no conditions

        h.agents.createCustom("Mine", null, "x", null);
        assertThat(h.agents.ownerCreated()).hasSize(2);
        h.agents.deleteOwnerCreated();
        assertThat(h.agents.all()).extracting(AgentProfile::id).containsExactly("weekend");
    }
}
