package com.weekend.assistant.port;

import com.weekend.assistant.domain.Message;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Chat turns (Firestore collection {@code message}). */
public interface MessageRepository {
    Message save(Message message);
    /** Most recent {@code limit} messages of a conversation, oldest first. */
    List<Message> recent(String conversationId, int limit);
    List<Message> findAll();
    BigDecimal costSince(Instant since);
    int deleteOlderThan(Instant cutoff);
    void deleteAll();
}
