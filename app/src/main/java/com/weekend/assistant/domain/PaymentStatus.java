package com.weekend.assistant.domain;

/** Payment tracking states. Weekend never moves money; PAID is set by the owner after paying elsewhere. */
public enum PaymentStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    PAID
}
