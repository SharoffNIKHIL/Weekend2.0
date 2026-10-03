package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.port.MemoryRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Keyword-overlap search; good enough for dev and tests. Vector search arrives with Firestore. */
@Repository
public class InMemoryMemoryRepository implements MemoryRepository {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "to", "of", "and", "or", "in", "on", "for", "my", "me", "i",
            "what", "do", "you", "know", "about", "it", "that", "this", "with");

    private final Map<String, Memory> store = new ConcurrentHashMap<>();

    @Override
    public Memory save(Memory memory) {
        store.put(memory.id(), memory);
        return memory;
    }

    @Override
    public Optional<Memory> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Memory> findAll() {
        return store.values().stream().sorted(Comparator.comparing(Memory::createdAt).reversed()).toList();
    }

    @Override
    public List<Memory> search(String query, int limit) {
        Set<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        return store.values().stream()
                .map(m -> Map.entry(m, overlap(terms, terms(m.text()))))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<Memory, Long>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().createdAt(), Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public boolean delete(String id) {
        return store.remove(id) != null;
    }

    @Override
    public int deleteExpired(Instant now) {
        List<String> expired = store.values().stream()
                .filter(m -> !m.pinned() && m.expiresAt() != null && !m.expiresAt().isAfter(now))
                .map(Memory::id)
                .toList();
        expired.forEach(store::remove);
        return expired.size();
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    private static Set<String> terms(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() > 1 && !STOP_WORDS.contains(t))
                .collect(Collectors.toSet());
    }

    private static long overlap(Set<String> a, Set<String> b) {
        return a.stream().filter(b::contains).count();
    }
}
