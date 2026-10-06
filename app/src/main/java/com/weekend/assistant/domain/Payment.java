package com.weekend.assistant.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A payment to track and approve (bill, subscription, transfer). Tracking only: no card or bank data is
 * stored and nothing is ever paid by Weekend. Amounts are in INR.
 */
public record Payment(
        String id,
        String payee,
        BigDecimal amountInr,
        Instant dueAt,
        String note,
        PaymentStatus status,
        Instant createdAt,
        Instant decidedAt) {

    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(payee, "payee");
        Objects.requireNonNull(amountInr, "amountInr");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Payment withStatus(PaymentStatus next, Instant when) {
        return new Payment(id, payee, amountInr, dueAt, note, next, createdAt, when);
    }
}
