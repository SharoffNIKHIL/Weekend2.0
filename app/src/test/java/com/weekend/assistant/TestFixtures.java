package com.weekend.assistant;

import com.weekend.assistant.config.WeekendProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/** Shared test helpers: default properties and a clock tests can move forward. */
public final class TestFixtures {

    public static final String KEY = "test-only-session-key-0123456789abcdef";

    private TestFixtures() {}

    public static WeekendProperties props() {
        return props(new BigDecimal("0.62"));
    }

    public static WeekendProperties props(BigDecimal dailyCap) {
        return props(dailyCap, agents(List.of(), List.of()));
    }

    public static WeekendProperties.Agents agents(List<String> allowedHosts, List<WeekendProperties.CustomAgent> custom) {
        return new WeekendProperties.Agents(allowedHosts, Duration.ofSeconds(5), custom);
    }

    public static WeekendProperties props(WeekendProperties.Agents agents) {
        return props(new BigDecimal("0.62"), agents);
    }

    public static WeekendProperties props(BigDecimal dailyCap, WeekendProperties.Agents agents) {
        return new WeekendProperties(
                "Asia/Kolkata",
                new WeekendProperties.Llm("local", "", "global", "claude-haiku-4-5@20251001", "claude-sonnet-5", 1024,
                        new BigDecimal("1.00"), new BigDecimal("5.00"), new BigDecimal("2.00"), new BigDecimal("10.00")),
                new WeekendProperties.Agent(5, 8, 20, 1200, dailyCap),
                new WeekendProperties.Retention(Duration.ofDays(365), Duration.ofDays(180), Duration.ofDays(90), Duration.ofDays(730),
                        Duration.ofDays(30), Duration.ofDays(365)),
                new WeekendProperties.Security(KEY, Duration.ofHours(12), true),
                agents);
    }

    /** A clock fixed at a moment that tests can advance. */
    public static final class MutableClock extends Clock {
        private Instant now;

        public MutableClock(Instant start) {
            this.now = start;
        }

        public void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
