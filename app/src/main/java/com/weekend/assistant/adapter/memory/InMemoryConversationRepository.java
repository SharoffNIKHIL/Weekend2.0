package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.Conversation;
import com.weekend.assistant.port.ConversationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** Process-local store for dev and tests. Replaced by Firestore in the database feature. */
@Repository
public class InMemoryConversationRepository implements ConversationRepository {

    private final Map<String, Conversation> store = new ConcurrentHashMap<>();

    @Override
    public Conversation save(Conversation conversation) {
        store.put(conversation.id(), conversation);
        return conversation;
    }

    @Override
    public Optional<Conversation> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Conversation> findAll() {
        List<Conversation> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(Conversation::createdAt).reversed());
        return all;
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
