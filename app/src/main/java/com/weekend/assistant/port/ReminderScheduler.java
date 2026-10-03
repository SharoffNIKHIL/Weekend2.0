package com.weekend.assistant.port;

import com.weekend.assistant.domain.Reminder;

/** Schedules delivery of a reminder. Production: one Cloud Task per reminder (networking feature). */
public interface ReminderScheduler {
    void schedule(Reminder reminder);
    void cancel(String reminderId);
}
