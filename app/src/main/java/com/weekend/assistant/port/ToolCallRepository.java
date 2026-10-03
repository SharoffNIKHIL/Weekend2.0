package com.weekend.assistant.port;

import com.weekend.assistant.domain.ToolCallRecord;
import java.time.Instant;
import java.util.List;

/** Tool-call log (P5: 90-day retention). */
public interface ToolCallRepository {
    ToolCallRecord save(ToolCallRecord record);
    List<ToolCallRecord> findAll();
    int deleteOlderThan(Instant cutoff);
    void deleteAll();
}
