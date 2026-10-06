package com.weekend.assistant.workspace;

import com.weekend.assistant.domain.NotificationKind;
import com.weekend.assistant.domain.Payment;
import com.weekend.assistant.domain.PaymentStatus;
import com.weekend.assistant.inbox.NotificationService;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.PaymentRepository;
import com.weekend.assistant.security.SecretFilter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Payment tracking and approval. Weekend NEVER moves money: there is no payment gateway, and no card or bank
 * details are stored. Flow: PENDING_APPROVAL → APPROVED / REJECTED; APPROVED → PAID (the owner marks it after
 * paying elsewhere). Every request raises an approval notification.
 */
@Service
public class PaymentService {

    static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000"); // ₹1 crore sanity cap

    private final PaymentRepository repo;
    private final NotificationService notifications;
    private final SecretFilter secrets;
    private final AuditLog audit;
    private final Clock clock;

    public PaymentService(PaymentRepository repo, NotificationService notifications, SecretFilter secrets, AuditLog audit, Clock clock) {
        this.repo = repo;
        this.notifications = notifications;
        this.secrets = secrets;
        this.audit = audit;
        this.clock = clock;
    }

    public Payment request(String payee, BigDecimal amountInr, Instant dueAt, String note, String actor) {
        String p = Inputs.required(payee, "payee", 120);
        if (secrets.containsSecret(p) || (note != null && secrets.containsSecret(note))) {
            throw new IllegalArgumentException("payments must not contain card numbers, keys or other secrets");
        }
        if (amountInr == null || amountInr.signum() <= 0 || amountInr.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("amountInr must be between 0.01 and " + MAX_AMOUNT.toPlainString());
        }
        Payment pay = repo.save(new Payment(UUID.randomUUID().toString(), p, amountInr.setScale(2, RoundingMode.HALF_UP),
                dueAt, Inputs.optional(note, "note", 500), PaymentStatus.PENDING_APPROVAL, clock.instant(), null));
        audit.append(actor, "payment.request", pay.id());
        notifications.notify(NotificationKind.APPROVAL, "Payment needs your approval",
                pay.payee() + " · ₹" + pay.amountInr().toPlainString(), "#approvals");
        return pay;
    }

    public List<Payment> all() {
        return repo.findAll();
    }

    public List<Payment> pendingApproval() {
        return repo.findAll().stream().filter(p -> p.status() == PaymentStatus.PENDING_APPROVAL).toList();
    }

    public Optional<Payment> decide(String id, boolean approved) {
        return transition(id, PaymentStatus.PENDING_APPROVAL, approved ? PaymentStatus.APPROVED : PaymentStatus.REJECTED);
    }

    public Optional<Payment> markPaid(String id) {
        return transition(id, PaymentStatus.APPROVED, PaymentStatus.PAID);
    }

    public boolean delete(String id) {
        boolean removed = repo.deleteById(id);
        if (removed) {
            audit.append("owner", "payment.delete", id);
        }
        return removed;
    }

    private Optional<Payment> transition(String id, PaymentStatus from, PaymentStatus to) {
        return repo.findById(id).filter(p -> p.status() == from).map(p -> {
            audit.append("owner", "payment." + to.name().toLowerCase(java.util.Locale.ROOT), id);
            return repo.save(p.withStatus(to, clock.instant()));
        });
    }
}
