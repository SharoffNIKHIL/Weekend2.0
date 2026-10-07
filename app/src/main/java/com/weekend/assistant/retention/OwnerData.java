package com.weekend.assistant.retention;

import java.time.Instant;

/** A component that holds owner data outside the core repositories (e.g. rendered videos): exported, purged, deleted. */
public interface OwnerData {

    String name();

    Object export();

    /** P5: removes items past their own retention period at {@code now}; returns how many. */
    int purgeExpired(Instant now);

    /** P6: removes everything. */
    void deleteAll();
}
