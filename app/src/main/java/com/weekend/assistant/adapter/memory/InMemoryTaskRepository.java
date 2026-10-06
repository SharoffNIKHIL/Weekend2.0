package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.Task;
import com.weekend.assistant.port.TaskRepository;
import java.time.Instant;
import java.util.Comparator;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTaskRepository extends InMemoryEntityRepository<Task> implements TaskRepository {
    public InMemoryTaskRepository() {
        super(Task::id, Comparator.comparing(Task::status)
                .thenComparing(Task::dueAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                .thenComparing(Task::createdAt));
    }
}
