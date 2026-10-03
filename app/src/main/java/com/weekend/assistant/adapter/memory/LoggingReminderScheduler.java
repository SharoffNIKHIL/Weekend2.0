package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.port.ReminderScheduler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dev scheduler: records what would be scheduled. Cloud Tasks adapter comes in the networking feature. */
@Component
public class LoggingReminderScheduler implements ReminderScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingReminderScheduler.class);

    private final Set<String> scheduled = ConcurrentHashMap.newKeySet();

    @Override
    public void schedule(Reminder reminder) {
        scheduled.add(reminder.id());
        LOG.info("reminder scheduled id={} dueAt={}", reminder.id(), reminder.dueAt());
    }

    @Override
    public void cancel(String reminderId) {
        scheduled.remove(reminderId);
        LOG.info("reminder cancelled id={}", reminderId);
    }

    public boolean isScheduled(String reminderId) {
        return scheduled.contains(reminderId);
    }
}
