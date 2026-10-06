package com.weekend.assistant.web;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.Payment;
import com.weekend.assistant.workspace.PaymentService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Payment tracking and approval. Nothing here moves money. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;
    private final WeekendProperties props;

    public PaymentController(PaymentService payments, WeekendProperties props) {
        this.payments = payments;
        this.props = props;
    }

    /** dueDate: "2026-10-15" (optional). */
    public record PaymentRequest(String payee, BigDecimal amountInr, String dueDate, String note) {}

    public record Decision(boolean approved) {}

    @GetMapping
    public List<Payment> list() {
        return payments.all();
    }

    @PostMapping
    public ResponseEntity<Payment> create(@RequestBody PaymentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(payments.request(req.payee(), req.amountInr(), Dates.localDate(req.dueDate(), props.zone()), req.note(), "owner"));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<Payment> decide(@PathVariable String id, @RequestBody Decision req) {
        return ResponseEntity.of(payments.decide(id, req.approved()));
    }

    @PostMapping("/{id}/paid")
    public ResponseEntity<Payment> paid(@PathVariable String id) {
        return ResponseEntity.of(payments.markPaid(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return payments.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
