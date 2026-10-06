package com.weekend.assistant.config;

import com.weekend.assistant.agent.AgentService;
import com.weekend.assistant.agents.AgentDirectory;
import com.weekend.assistant.domain.AgentProfile;
import com.weekend.assistant.domain.Folder;
import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.MemoryKind;
import com.weekend.assistant.domain.NotificationKind;
import com.weekend.assistant.domain.Payment;
import com.weekend.assistant.domain.Task;
import com.weekend.assistant.domain.TaskPriority;
import com.weekend.assistant.inbox.InboxService;
import com.weekend.assistant.inbox.NotificationService;
import com.weekend.assistant.memory.MemoryService;
import com.weekend.assistant.reminder.ReminderService;
import com.weekend.assistant.workspace.FolderService;
import com.weekend.assistant.workspace.PaymentService;
import com.weekend.assistant.workspace.TaskService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * UI preview environment ({@code ui} profile, used with {@code local}): fills the in-memory stores with
 * made-up demo data so every screen can be reviewed populated, and connects the loopback demo agent. Refuses to
 * run against a real model or with sessions switched on, so it can never mix demo rows into a real environment.
 */
@Component
@Profile("ui")
public class UiPreviewSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UiPreviewSeeder.class);

    private final MemoryService memories;
    private final ReminderService reminders;
    private final FolderService folders;
    private final TaskService tasks;
    private final PaymentService payments;
    private final InboxService inbox;
    private final NotificationService notifications;
    private final AgentDirectory agents;
    private final AgentService agent;
    private final WeekendProperties props;
    private final Clock clock;
    private final Environment env;

    public UiPreviewSeeder(MemoryService memories, ReminderService reminders, FolderService folders, TaskService tasks,
            PaymentService payments, InboxService inbox, NotificationService notifications, AgentDirectory agents,
            AgentService agent, WeekendProperties props, Clock clock, Environment env) {
        this.memories = memories;
        this.reminders = reminders;
        this.folders = folders;
        this.tasks = tasks;
        this.payments = payments;
        this.inbox = inbox;
        this.notifications = notifications;
        this.agents = agents;
        this.agent = agent;
        this.props = props;
        this.clock = clock;
        this.env = env;
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
                .stream().flatMap(Optional::stream).toList();
        memories.pin(seeded.get(0).id(), true);
        memories.pin(seeded.get(4).id(), true);

        Instant now = clock.instant();
        Folder work = folders.create("Work", "work");
        Folder home = folders.create("Home", "home");
        Folder money = folders.create("Money", "money");
        Folder health = folders.create("Health", "health");
        Folder travel = folders.create("Travel", "travel");

        tasks.create("Review Terraform plan for dev", null, work.id(), now.plus(Duration.ofHours(5)), TaskPriority.HIGH, "owner");
        tasks.create("Write the Feature_database ADR", null, work.id(), now.plus(Duration.ofDays(2)), TaskPriority.NORMAL, "owner");
        Task done = tasks.create("Merge feature_cicd PR", null, work.id(), null, TaskPriority.NORMAL, "owner");
        tasks.complete(done.id());
        tasks.create("Fix the balcony tap", null, home.id(), now.plus(Duration.ofDays(4)), TaskPriority.LOW, "owner");
        tasks.create("Order groceries", null, home.id(), null, TaskPriority.NORMAL, "owner");
        tasks.create("Book annual health check-up", null, health.id(), now.plus(Duration.ofDays(9)), TaskPriority.NORMAL, "owner");
        tasks.create("Renew passport", null, travel.id(), now.plus(Duration.ofDays(20)), TaskPriority.HIGH, "owner");
        tasks.create("Call the bank about FD renewal", null, null, null, TaskPriority.NORMAL, "owner");

        reminders.create("Rotate the staging service-account key", now.plus(Duration.ofHours(20)), work.id());
        reminders.create("Review the GCP billing report", now.plus(Duration.ofDays(3)), money.id());
        reminders.create("Renew the domain certificate", now.plus(Duration.ofDays(12)), work.id());
        reminders.create("Pay electricity bill", now.plus(Duration.ofDays(2)), money.id());
        reminders.create("Take vitamin D", now.plus(Duration.ofHours(14)), health.id());
        reminders.create("Check Cloud Run cold-start logs", now.plus(Duration.ofDays(1)))
                .ifPresent(r -> reminders.cancel(r.id()));

        Payment rent = payments.request("House rent", new BigDecimal("25000"), now.plus(Duration.ofDays(5)), "October", "owner");
        payments.request("Electricity bill", new BigDecimal("1840.50"), now.plus(Duration.ofDays(2)), null, "owner");
        Payment cloud = payments.request("Google Cloud", new BigDecimal("2784"), now.plus(Duration.ofDays(12)), "Weekend 2.0 prod estimate", "owner");
        payments.decide(rent.id(), true);
        payments.decide(cloud.id(), true);
        payments.markPaid(cloud.id());

        agent.chat(null, "Remind me to renew the car insurance", false);

        // Read at run time: the web server has picked its (possibly random) port by now.
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8081"));
        String endpoint = "http://127.0.0.1:" + port + "/demo-agent";
        AgentProfile demo = agents.connectRemote("Demo agent", "A stand-in for another agent; runs inside this preview on loopback.", endpoint).agent();
        inbox.receive(demo.id(), demo.name(), "Hello from the demo agent", "I am connected. Ask me something from Chat by picking \"Demo agent\".");
        inbox.receive("system", "Weekend", "Welcome to the UI preview", "Everything here is made-up demo data held in memory. Restart to reset.");
        notifications.notify(NotificationKind.TASK, "Task due soon", "Review Terraform plan for dev", "#tasks");
        notifications.notify(NotificationKind.SYSTEM, "UI preview", "Demo data loaded", "#home");

        log.info("ui preview: seeded demo memories, {} folders, tasks, reminders, payments, inbox and the demo agent (in memory only)",
                folders.summaries().size());
    }
}
