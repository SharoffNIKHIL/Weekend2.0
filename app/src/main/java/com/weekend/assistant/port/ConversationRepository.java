package com.weekend.assistant.port;

import com.weekend.assistant.domain.Conversation;
import java.util.List;
import java.util.Optional;

/** Conversations (Firestore collection {@code conversation} in the database feature). */
public interface ConversationRepository {
    Conversation save(Conversation conversation);
    Optional<Conversation> findById(String id);
    List<Conversation> findAll();
    void deleteAll();
}
