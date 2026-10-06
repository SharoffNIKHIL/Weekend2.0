package com.weekend.assistant.web;

import com.weekend.assistant.domain.InboxMessage;
import com.weekend.assistant.domain.Notification;
import com.weekend.assistant.inbox.InboxService;
import com.weekend.assistant.inbox.NotificationService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Messages from agents, and in-app notifications. */
@RestController
@RequestMapping("/api")
public class InboxController {

    private final InboxService inbox;
    private final NotificationService notifications;

    public InboxController(InboxService inbox, NotificationService notifications) {
        this.inbox = inbox;
        this.notifications = notifications;
    }

    @GetMapping("/messages")
    public List<InboxMessage> messages() {
        return inbox.all();
    }

    @PostMapping("/messages/{id}/read")
    public ResponseEntity<InboxMessage> readMessage(@PathVariable String id) {
        return ResponseEntity.of(inbox.markRead(id));
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        return inbox.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/notifications")
    public List<Notification> notifications() {
        return notifications.all();
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Notification> readNotification(@PathVariable String id) {
        return ResponseEntity.of(notifications.markRead(id));
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("marked", notifications.markAllRead());
    }

    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable String id) {
        return notifications.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
