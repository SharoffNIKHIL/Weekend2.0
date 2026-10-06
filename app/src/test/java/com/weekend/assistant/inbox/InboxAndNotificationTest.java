package com.weekend.assistant.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekend.assistant.Harness;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.domain.InboxMessage;
import com.weekend.assistant.domain.NotificationKind;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InboxAndNotificationTest {

    private final Harness h = new Harness(new ScriptedLlmProvider());

    @Test
    void receivedMessagesAreRedactedClippedAuditedAndNotified() {
        InboxMessage m = h.inbox.receive("remote-1", "Helper", null, "token: abc123 and " + "x".repeat(9000));
        assertThat(m.subject()).isEqualTo("(no subject)");
        assertThat(m.body()).startsWith("[REDACTED]").hasSize(InboxService.MAX_BODY);
        assertThat(m.read()).isFalse();
        assertThat(h.notifications.all()).singleElement().satisfies(n -> {
            assertThat(n.kind()).isEqualTo(NotificationKind.MESSAGE);
            assertThat(n.title()).isEqualTo("New message from Helper");
        });
        assertThat(h.audit.findAll()).extracting("actor").contains("agent:remote-1");
    }

    @Test
    void readDeleteAndUnreadCounts() {
        InboxMessage a = h.inbox.receive("system", "Weekend", "A", "a");
        h.inbox.receive("system", "Weekend", "B", "b");
        assertThat(h.inbox.unreadCount()).isEqualTo(2);
        assertThat(h.inbox.markRead(a.id())).get().extracting(InboxMessage::read).isEqualTo(true);
        assertThat(h.inbox.unreadCount()).isEqualTo(1);
        assertThat(h.inbox.markRead("missing")).isEmpty();
        assertThat(h.inbox.delete(a.id())).isTrue();
        assertThat(h.inbox.delete(a.id())).isFalse();

        assertThat(h.notifications.unreadCount()).isEqualTo(2);
        String first = h.notifications.all().get(0).id();
        assertThat(h.notifications.markRead(first)).isPresent();
        assertThat(h.notifications.markAllRead()).isEqualTo(1);
        assertThat(h.notifications.unreadCount()).isZero();
        assertThat(h.notifications.delete(first)).isTrue();
    }

    @Test
    void notificationTextIsClipped() {
        assertThat(h.notifications.notify(NotificationKind.SYSTEM, "t".repeat(400), null, null).title()).hasSize(300);
    }

    @Test
    void retentionRemovesOldNotificationsAndInboxMessages() {
        h.inbox.receive("system", "Weekend", "old", "old");           // + 1 notification
        h.clock.advance(Duration.ofDays(31));
        h.notifications.notify(NotificationKind.SYSTEM, "fresh", null, null);
        var report = h.retention.run();
        assertThat(report.notificationsDeleted()).isEqualTo(1);
        assertThat(report.inboxMessagesDeleted()).isZero();
        h.clock.advance(Duration.ofDays(340));
        assertThat(h.retention.run().inboxMessagesDeleted()).isEqualTo(1);
        assertThat(h.inbox.all()).isEmpty();
    }

    @Test
    void reminderDeliveryRaisesANotification() {
        String id = h.reminders.create("Stretch", h.clock.instant().plus(Duration.ofMinutes(5))).orElseThrow().id();
        h.reminders.markDelivered(id);
        assertThat(h.notifications.all()).extracting("kind").containsExactly(NotificationKind.REMINDER);
        h.reminders.markDelivered(id); // idempotent: no second notification
        assertThat(h.notifications.all()).hasSize(1);
    }
}
