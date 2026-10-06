package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.Payment;
import com.weekend.assistant.port.PaymentRepository;
import java.time.Instant;
import java.util.Comparator;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPaymentRepository extends InMemoryEntityRepository<Payment> implements PaymentRepository {
    public InMemoryPaymentRepository() {
        super(Payment::id, Comparator.comparing(Payment::dueAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                .thenComparing(Payment::createdAt));
    }
}
