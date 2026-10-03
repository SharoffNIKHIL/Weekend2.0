package com.weekend.assistant.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekend.assistant.Harness;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.MemoryKind;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MemoryServiceTest {

    private final Harness h = new Harness(new ScriptedLlmProvider());

    @Test
    void savesFactsButRejectsSecrets() {
        assertThat(h.memories.remember("The staging DB rotates on the 15th", MemoryKind.FACT, null)).isPresent();
        assertThat(h.memories.remember("my password is hunter2", MemoryKind.FACT, null)).isEmpty();
        assertThat(h.memories.remember("   ", MemoryKind.FACT, null)).isEmpty();
        assertThat(h.memories.all()).hasSize(1);
        assertThat(h.audit.verify()).isTrue();
    }

    @Test
    void extractsExplicitRememberRequests() {
        assertThat(h.memories.extractExplicit("Remember that I prefer tea", "msg-1")).get()
                .extracting(Memory::text).isEqualTo("I prefer tea");
        assertThat(h.memories.extractExplicit("What is the weather?", "msg-2")).isEmpty();
    }

    @Test
    void searchFindsByKeywordAndPinningStopsExpiry() {
        Memory m = h.memories.remember("Staging database rotates monthly", MemoryKind.FACT, null).orElseThrow();
        assertThat(h.memories.search("when does the staging database rotate", 8)).extracting(Memory::id).containsExactly(m.id());
        assertThat(h.memories.pin(m.id(), true)).get().extracting(Memory::expiresAt).isNull();

        Memory other = h.memories.remember("Unpinned note about lunch", MemoryKind.FACT, null).orElseThrow();
        h.clock.advance(Duration.ofDays(181));
        assertThat(h.retention.run().memoriesDeleted()).isEqualTo(1);
        assertThat(h.memories.all()).extracting(Memory::id).containsExactly(m.id()).doesNotContain(other.id());
    }

    @Test
    void deleteIsAudited() {
        Memory m = h.memories.remember("Temporary fact", MemoryKind.FACT, null).orElseThrow();
        assertThat(h.memories.delete(m.id())).isTrue();
        assertThat(h.memories.delete(m.id())).isFalse();
        assertThat(h.audit.findAll()).extracting("action").contains("memory.delete");
    }
}
