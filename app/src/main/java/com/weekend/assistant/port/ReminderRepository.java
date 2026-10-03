package com.weekend.assistant.port;

import com.weekend.assistant.domain.Reminder;
import java.util.List;
import java.util.Optional;

/** Reminders (Firestore collection {@code reminder}). */
public interface ReminderRepository {
    Reminder save(Reminder reminder);
    Optional<Reminder> findById(String id);
    List<Reminder> findAll();
    void deleteAll();
}
