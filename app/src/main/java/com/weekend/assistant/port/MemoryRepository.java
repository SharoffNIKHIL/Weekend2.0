package com.weekend.assistant.port;

import com.weekend.assistant.domain.Memory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Memories. The in-memory adapter ranks by keyword overlap; Firestore adds vector (KNN) search. */
public interface MemoryRepository {
    Memory save(Memory memory);
    Optional<Memory> findById(String id);
    List<Memory> findAll();
    List<Memory> search(String query, int limit);
    boolean delete(String id);
    int deleteExpired(Instant now);
    void deleteAll();
}
