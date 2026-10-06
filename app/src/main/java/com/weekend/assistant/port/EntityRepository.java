package com.weekend.assistant.port;

import java.util.List;
import java.util.Optional;

/** Common storage operations for the workspace entities (Firestore collections in Feature_database). */
public interface EntityRepository<T> {
    T save(T entity);
    Optional<T> findById(String id);
    List<T> findAll();
    boolean deleteById(String id);
    void deleteAll();
}
