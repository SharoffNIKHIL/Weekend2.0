package com.weekend.assistant.adapter.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekend.assistant.domain.AuditEntry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryAuditLogTest {

    private final InMemoryAuditLog log = new InMemoryAuditLog(Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void chainVerifiesAndLinks() {
        AuditEntry a = log.append("owner", "memory.create", "m1");
        AuditEntry b = log.append("owner", "memory.delete", "m1");
        assertThat(a.hashPrev()).isEqualTo(InMemoryAuditLog.GENESIS);
        assertThat(b.hashPrev()).isEqualTo(a.hashSelf());
        assertThat(log.verify()).isTrue();
    }

    @Test
    void detectsTampering() {
        log.append("owner", "memory.create", "m1");
        AuditEntry second = log.append("owner", "data.export", "all");
        log.append("system", "retention.run", "x");
        log.replaceForTest(1, new AuditEntry(second.seq(), second.ts(), "attacker", second.action(), second.target(),
                second.hashPrev(), second.hashSelf()));
        assertThat(log.verify()).isFalse();
    }
}
