package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.Message;
import com.weekend.assistant.port.MessageRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryMessageRepository implements MessageRepository {

    private final List<Message> store = new CopyOnWriteArrayList<>();

    @Override
    public Message save(Message message) {
        store.add(message);
        return message;
    }

    @Override
    public List<Message> recent(String conversationId, int limit) {
        List<Message> forConversation = store.stream()
                .filter(m -> m.conversationId().equals(conversationId))
                .sorted(Comparator.comparing(Message::createdAt))
                .toList();
        int from = Math.max(0, forConversation.size() - limit);
        return forConversation.subList(from, forConversation.size());
    }

    @Override
    public List<Message> findAll() {
        return store.stream().sorted(Comparator.comparing(Message::createdAt)).toList();
    }

    @Override
    public BigDecimal costSince(Instant since) {
        return store.stream()
                .filter(m -> !m.createdAt().isBefore(since))
                .map(Message::costUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        List<Message> old = store.stream().filter(m -> m.createdAt().isBefore(cutoff)).toList();
        store.removeAll(old);
        return old.size();
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
