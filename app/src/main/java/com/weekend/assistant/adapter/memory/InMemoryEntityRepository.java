package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.port.EntityRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Thread-safe in-memory store for dev and tests; listing order comes from {@code order}. */
abstract class InMemoryEntityRepository<T> implements EntityRepository<T> {

    private final Map<String, T> store = new ConcurrentHashMap<>();
    private final Function<T, String> id;
    private final Comparator<T> order;

    InMemoryEntityRepository(Function<T, String> id, Comparator<T> order) {
        this.id = id;
        this.order = order;
    }

    @Override
    public T save(T entity) {
        store.put(id.apply(entity), entity);
        return entity;
    }

    @Override
    public Optional<T> findById(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(store.get(key));
    }

    @Override
    public List<T> findAll() {
        return store.values().stream().sorted(order).toList();
    }

    @Override
    public boolean deleteById(String key) {
        return key != null && store.remove(key) != null;
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
