package com.weekend.assistant.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.Harness;
import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.domain.NotificationKind;
import com.weekend.assistant.domain.Payment;
import com.weekend.assistant.domain.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private final Harness h = new Harness(new ScriptedLlmProvider());

    @Test
    void requestStartsPendingRoundsAndRaisesAnApprovalNotification() {
        Payment p = h.payments.request("Rent", new BigDecimal("25000.456"), null, "October", "owner");
        assertThat(p.status()).isEqualTo(PaymentStatus.PENDING_APPROVAL);
        assertThat(p.amountInr()).isEqualByComparingTo("25000.46");
        assertThat(h.notifications.all()).singleElement().satisfies(n -> {
            assertThat(n.kind()).isEqualTo(NotificationKind.APPROVAL);
            assertThat(n.link()).isEqualTo("#approvals");
        });
    }

    @Test
    void rejectsBadAmountsAndCardNumbers() {
        assertThatThrownBy(() -> h.payments.request("X", BigDecimal.ZERO, null, null, "owner")).hasMessageContaining("amountInr");
        assertThatThrownBy(() -> h.payments.request("X", new BigDecimal("-5"), null, null, "owner")).hasMessageContaining("amountInr");
        assertThatThrownBy(() -> h.payments.request("X", null, null, null, "owner")).hasMessageContaining("amountInr");
        assertThatThrownBy(() -> h.payments.request("X", new BigDecimal("10000000.01"), null, null, "owner")).hasMessageContaining("amountInr");
        assertThatThrownBy(() -> h.payments.request("Card", BigDecimal.TEN, null, "card 4111 1111 1111 1111", "owner"))
                .hasMessageContaining("card numbers");
        assertThatThrownBy(() -> h.payments.request("", BigDecimal.TEN, null, null, "owner")).hasMessageContaining("payee");
        assertThat(h.payments.all()).isEmpty();
    }

    @Test
    void lifecycleApproveThenPaidAndRejectIsFinal() {
        Payment a = h.payments.request("A", BigDecimal.ONE, null, null, "owner");
        assertThat(h.payments.markPaid(a.id())).isEmpty();                 // not approved yet
        assertThat(h.payments.decide(a.id(), true)).get().extracting(Payment::status).isEqualTo(PaymentStatus.APPROVED);
        assertThat(h.payments.decide(a.id(), false)).isEmpty();            // already decided
        assertThat(h.payments.markPaid(a.id())).get().satisfies(p -> {
            assertThat(p.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(p.decidedAt()).isNotNull();
        });

        Payment b = h.payments.request("B", BigDecimal.ONE, null, null, "owner");
        assertThat(h.payments.decide(b.id(), false)).get().extracting(Payment::status).isEqualTo(PaymentStatus.REJECTED);
        assertThat(h.payments.markPaid(b.id())).isEmpty();
        assertThat(h.payments.pendingApproval()).isEmpty();
        assertThat(h.payments.delete(b.id())).isTrue();
        assertThat(h.audit.findAll()).extracting("action").contains("payment.request", "payment.approved", "payment.paid", "payment.rejected");
    }
}
