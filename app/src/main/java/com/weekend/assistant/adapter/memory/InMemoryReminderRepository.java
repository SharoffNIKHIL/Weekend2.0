package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.port.ReminderRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryReminderRepository implements ReminderRepository {

    private final Map<String, Reminder> store = new ConcurrentHashMap<>();

    @Override
    public Reminder save(Reminder reminder) {
        store.put(reminder.id(), reminder);
        return reminder;
    }

    @Override
    public Optional<Reminder> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Reminder> findAll() {
        return store.values().stream().sorted(Comparator.comparing(Reminder::dueAt)).toList();
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
