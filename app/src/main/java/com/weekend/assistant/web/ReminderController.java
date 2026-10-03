package com.weekend.assistant.web;

import com.weekend.assistant.domain.Reminder;
import com.weekend.assistant.reminder.ReminderService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** List and cancel reminders. Creation goes through the agent (reminder_create needs confirmation). */
@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminders;

    public ReminderController(ReminderService reminders) {
        this.reminders = reminders;
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
