package com.weekend.assistant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.TestFixtures.MutableClock;
import com.weekend.assistant.config.WeekendProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionTokenServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-03T00:00:00Z"));
    private final SessionTokenService tokens = new SessionTokenService(TestFixtures.props(), clock);

    @Test
    void issuedTokenVerifiesUntilExpiry() {
        String t = tokens.issue();
        assertThat(tokens.verify(t)).isPresent();
        clock.advance(Duration.ofHours(12).plusSeconds(1));
        assertThat(tokens.verify(t)).isEmpty();
    }

    @Test
    void rejectsTamperedOrForeignTokens() {
        String t = tokens.issue();
        String tampered = t.substring(0, t.length() - 2) + (t.endsWith("A") ? "BB" : "AA");
        assertThat(tokens.verify(tampered)).isEmpty();
        assertThat(tokens.verify("garbage")).isEmpty();
        assertThat(tokens.verify(null)).isEmpty();

        WeekendProperties other = new WeekendProperties(TestFixtures.props().ownerTimezone(), TestFixtures.props().llm(),
                TestFixtures.props().agent(), TestFixtures.props().retention(),
                new WeekendProperties.Security("a-completely-different-key-0123456789", Duration.ofHours(12), true));
        assertThat(new SessionTokenService(other, clock).verify(t)).isEmpty();
    }

    @Test
    void failsClosedWithoutAStrongKey() {
        WeekendProperties weak = new WeekendProperties("Asia/Kolkata", TestFixtures.props().llm(), TestFixtures.props().agent(),
                TestFixtures.props().retention(), new WeekendProperties.Security("short", Duration.ofHours(1), true));
        assertThatThrownBy(() -> new SessionTokenService(weak, clock)).isInstanceOf(IllegalStateException.class);
    }
}
