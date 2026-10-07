package com.weekend.assistant.owner;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekend.assistant.Harness;
import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.MemoryKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnerProfileTest {

    @TempDir Path dir;

    @Test
    void parsesTypedLinesAndIgnoresEverythingElse() {
        var items = OwnerProfile.parse("""
                # heading
                - fact: DevOps since 2018
                - pref: Python first
                * preference: Short answers
                - task: Weekly review
                - plain line
                not a list item
                -
                """);
        assertThat(items).extracting(OwnerProfile.Item::kind)
                .containsExactly(MemoryKind.FACT, MemoryKind.PREFERENCE, MemoryKind.PREFERENCE, MemoryKind.TASK, MemoryKind.FACT);
        assertThat(items.get(1).text()).isEqualTo("Python first");
    }

    @Test
    void importsAsPinnedMemoriesOnceAndSkipsSecrets() throws IOException {
        Harness h = new Harness(new ScriptedLlmProvider());
        Path file = Files.writeString(dir.resolve("owner-profile.md"), "- fact: Lives in India\n- pref: Costs in INR and USD\n- token: abc123\n");
        OwnerProfile profile = new OwnerProfile(h.memories, TestFixtures.with(TestFixtures.props(), null, null,
                new WeekendProperties.Owner(file.toString(), null)));
        profile.run(null);
        assertThat(profile.loaded()).isEqualTo(2);
        assertThat(h.memories.all()).hasSize(2).allMatch(Memory::pinned).allMatch(m -> m.expiresAt() == null);
        profile.run(null);
        assertThat(h.memories.all()).hasSize(2);                          // no duplicates on restart
        assertThat(profile.importText("- fact: LIVES IN INDIA\n- fact: New fact")).isEqualTo(1);
    }

    @Test
    void missingOrUnsetFileIsFine() {
        Harness h = new Harness(new ScriptedLlmProvider());
        new OwnerProfile(h.memories, TestFixtures.props()).run(null);
        new OwnerProfile(h.memories, TestFixtures.with(TestFixtures.props(), null, null,
                new WeekendProperties.Owner(dir.resolve("nope.md").toString(), null))).run(null);
        assertThat(h.memories.all()).isEmpty();
    }
}
