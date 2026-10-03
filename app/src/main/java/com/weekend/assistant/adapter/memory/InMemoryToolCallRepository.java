package com.weekend.assistant.adapter.memory;

import com.weekend.assistant.domain.ToolCallRecord;
import com.weekend.assistant.port.ToolCallRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryToolCallRepository implements ToolCallRepository {

    private final List<ToolCallRecord> store = new CopyOnWriteArrayList<>();

    @Override
    public ToolCallRecord save(ToolCallRecord record) {
        store.add(record);
        return record;
    }

    @Override
    public List<ToolCallRecord> findAll() {
        return List.copyOf(store);
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        List<ToolCallRecord> old = store.stream().filter(r -> r.createdAt().isBefore(cutoff)).toList();
        store.removeAll(old);
        return old.size();
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
