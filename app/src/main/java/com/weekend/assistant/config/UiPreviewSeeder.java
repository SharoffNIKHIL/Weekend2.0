package com.weekend.assistant.config;

import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.MemoryKind;
import com.weekend.assistant.memory.MemoryService;
import com.weekend.assistant.reminder.ReminderService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * UI preview environment ({@code ui} profile, used with {@code local}): fills the in-memory stores with
 * made-up demo data so every screen can be reviewed populated. Refuses to run against a real model or
 * with sessions switched on, so it can never mix demo rows into a real environment.
 */
@Component
@Profile("ui")
public class UiPreviewSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UiPreviewSeeder.class);

    private final MemoryService memories;
    private final ReminderService reminders;
    private final WeekendProperties props;
    private final Clock clock;

    public UiPreviewSeeder(MemoryService memories, ReminderService reminders, WeekendProperties props, Clock clock) {
        this.memories = memories;
        this.reminders = reminders;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"local".equals(props.llm().provider()) || props.security().requireSession()) {
            throw new IllegalStateException("ui profile needs the offline model and the local profile (no sessions)");
        }
        List<Memory> seeded = List.of(
                        memories.remember("I prefer filter coffee, no sugar", MemoryKind.PREFERENCE, null),
                        memories.remember("Staging GCP project lives in asia-south1 (Mumbai)", MemoryKind.FACT, null),
                        memories.remember("Weekly review every Sunday at 7 pm IST", MemoryKind.TASK, null),
                        memories.remember("Prefers Python for scripts and Terraform for infra", MemoryKind.PREFERENCE, null),
                        memories.remember("Budget for Weekend 2.0 is ₹5,000 per month", MemoryKind.FACT, null))
                .stream().flatMap(java.util.Optional::stream).toList();
        memories.pin(seeded.get(0).id(), true);
        memories.pin(seeded.get(4).id(), true);

        Instant now = clock.instant();
        reminders.create("Rotate the staging service-account key", now.plus(Duration.ofHours(20)));
        reminders.create("Review the GCP billing report", now.plus(Duration.ofDays(3)));
        reminders.create("Renew the domain certificate", now.plus(Duration.ofDays(12)));
        reminders.create("Check Cloud Run cold-start logs", now.plus(Duration.ofDays(1)))
                .ifPresent(r -> reminders.cancel(r.id()));
        log.info("ui preview: seeded {} demo memories and 4 demo reminders (in memory only)", seeded.size());
    }
}
