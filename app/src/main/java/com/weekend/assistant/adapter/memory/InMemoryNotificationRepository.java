package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.Notification;
import com.weekend.assistant.port.NotificationRepository;
import java.util.Comparator;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryNotificationRepository extends InMemoryEntityRepository<Notification> implements NotificationRepository {
    public InMemoryNotificationRepository() {
        super(Notification::id, Comparator.comparing(Notification::createdAt).reversed());
    }
}
