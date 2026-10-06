package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.InboxMessage;
import com.weekend.assistant.port.InboxMessageRepository;
import java.util.Comparator;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryInboxMessageRepository extends InMemoryEntityRepository<InboxMessage> implements InboxMessageRepository {
    public InMemoryInboxMessageRepository() {
        super(InboxMessage::id, Comparator.comparing(InboxMessage::createdAt).reversed());
    }
}
