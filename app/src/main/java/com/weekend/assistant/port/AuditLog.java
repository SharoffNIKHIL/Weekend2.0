package com.weekend.assistant.port;

import com.weekend.assistant.domain.AuditEntry;
import java.util.List;

/** Append-only, hash-chained security log (P8). There is deliberately no update or delete. */
public interface AuditLog {
    AuditEntry append(String actor, String action, String target);
    List<AuditEntry> findAll();
    /** True when every entry's hash matches its content and links to the previous entry. */
    boolean verify();
}
