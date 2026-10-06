package com.weekend.assistant.web;

import com.weekend.assistant.config.WeekendProperties;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.reminder.ReminderService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * List, create, move and cancel reminders. The owner creates reminders here directly; the agent creates them only
 * through reminder_create, which needs the owner's confirmation.
 */
@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminders;
    private final WeekendProperties props;

    public ReminderController(ReminderService reminders, WeekendProperties props) {
        this.reminders = reminders;
        this.props = props;
    }

    /** dueLocal: "2026-10-15T09:00" in the owner's time zone. */
    public record ReminderRequest(String text, String dueLocal, String folderId) {}

    public record MoveRequest(String folderId) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ReminderRequest req) {
        return reminders.create(req.text(), Dates.localDateTime(req.dueLocal(), props.zone()), req.folderId())
                .<ResponseEntity<?>>map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r))
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of("error", "the reminder time must be in the future")));
    }

    @PutMapping("/{id}/folder")
    public ResponseEntity<Reminder> move(@PathVariable String id, @RequestBody MoveRequest req) {
        return ResponseEntity.of(reminders.move(id, req.folderId()));
    }

    @GetMapping
    public List<Reminder> list() {
        return reminders.all();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Reminder> cancel(@PathVariable String id) {
        return ResponseEntity.of(reminders.cancel(id));
    }
}
