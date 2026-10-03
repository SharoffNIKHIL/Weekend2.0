package com.weekend.assistant.web;

import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.reminder.ReminderService;
import com.weekend.assistant.retention.DataService;
import com.weekend.assistant.retention.RetentionService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Worker endpoints called by Cloud Scheduler / Cloud Tasks. On Cloud Run the worker service has
 * internal-only ingress and only the invoker service account holds run.invoker, so Google's IAM
 * has verified the caller's OIDC token before the request reaches this code.
 */
@RestController
@RequestMapping("/jobs")
public class JobsController {

    private final RetentionService retention;
    private final DataService data;
    private final ReminderService reminders;

    public JobsController(RetentionService retention, DataService data, ReminderService reminders) {
        this.retention = retention;
        this.data = data;
        this.reminders = reminders;
    }

    @PostMapping("/retention")
    public RetentionService.RetentionReport retention() {
        return retention.run();
    }

    /** Builds the export; writing it to GCS (CMEK bucket) is added with the database feature. */
    @PostMapping("/export")
    public Map<String, Object> export() {
        DataService.Export e = data.export("system");
        return Map.of("exportedAt", e.exportedAt().toString(), "messages", e.messages().size(), "memories", e.memories().size());
    }

    @PostMapping("/reminders/{id}/deliver")
    public ResponseEntity<Reminder> deliver(@PathVariable String id) {
        return ResponseEntity.of(reminders.markDelivered(id));
    }
}
