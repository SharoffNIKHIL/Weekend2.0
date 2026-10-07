package com.weekend.assistant;

import com.weekend.assistant.config.WeekendProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Shared test helpers: default properties and a clock tests can move forward. */
public final class TestFixtures {

    public static final String KEY = "test-only-session-key-0123456789abcdef";

    private TestFixtures() {}

    public static WeekendProperties props() {
        return props(new BigDecimal("0.62"));
    }

    public static WeekendProperties props(BigDecimal dailyCap) {
        return new WeekendProperties(
                "Asia/Kolkata",
                new WeekendProperties.Llm("local", "", "global", "claude-haiku-4-5@20251001", "claude-sonnet-5", 1024,
                        new BigDecimal("1.00"), new BigDecimal("5.00"), new BigDecimal("2.00"), new BigDecimal("10.00")),
                new WeekendProperties.Agent(5, 8, 20, 1200, dailyCap),
                new WeekendProperties.Retention(Duration.ofDays(365), Duration.ofDays(180), Duration.ofDays(90), Duration.ofDays(730),
                        Duration.ofDays(30), Duration.ofDays(365)),
                new WeekendProperties.Security(KEY, Duration.ofHours(12), true),
                null, null, null, null, null);
    }

    /** Same defaults with other pressure, web or owner settings. */
    public static WeekendProperties with(WeekendProperties p, WeekendProperties.Pressure pressure, WeekendProperties.Web web,
            WeekendProperties.Owner owner) {
        return new WeekendProperties(p.ownerTimezone(), p.llm(), p.agent(), p.retention(), p.security(), p.notion(),
                pressure, web, owner, p.studio());
    }

    public static WeekendProperties withNotion(WeekendProperties.Notion notion) {
        WeekendProperties p = props();
        return new WeekendProperties(p.ownerTimezone(), p.llm(), p.agent(), p.retention(), p.security(), notion,
                p.pressure(), p.web(), p.owner(), p.studio());
    }

    public static WeekendProperties withStudio(WeekendProperties.Studio studio) {
        WeekendProperties p = props();
        return new WeekendProperties(p.ownerTimezone(), p.llm(), p.agent(), p.retention(), p.security(), p.notion(),
                p.pressure(), p.web(), p.owner(), studio);
    }

    public static WeekendProperties withSecurity(WeekendProperties.Security security) {
        WeekendProperties p = props();
        return new WeekendProperties(p.ownerTimezone(), p.llm(), p.agent(), p.retention(), security, p.notion(),
                p.pressure(), p.web(), p.owner(), p.studio());
    }

    /** A solid-colour PNG, base64-encoded (no data-URL prefix). */
    public static String png(int w, int h, java.awt.Color c) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(img, "png", out);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
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
