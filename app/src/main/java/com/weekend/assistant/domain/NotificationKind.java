package com.weekend.assistant.domain;

/** What raised a notification; the UI uses it to pick an icon and a link. */
public enum NotificationKind {
    REMINDER,
    TASK,
    APPROVAL,
    PAYMENT,
    MESSAGE,
    SYSTEM
}
