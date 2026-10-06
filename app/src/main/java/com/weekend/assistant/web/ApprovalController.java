package com.weekend.assistant.web;

import com.weekend.assistant.workspace.ApprovalService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Everything waiting on the owner's yes: agent tool calls and payment requests. */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    public record Decision(boolean approved) {}

    @GetMapping
    public List<ApprovalService.Approval> list() {
        return approvals.pending();
    }

    /** type: tool | payment. */
    @PostMapping("/{type}/{id}")
    public ResponseEntity<Map<String, String>> decide(@PathVariable String type, @PathVariable String id, @RequestBody Decision req) {
        ApprovalService.Type t;
        try {
            t = ApprovalService.Type.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "type must be tool or payment"));
        }
        return approvals.decide(t, id, req.approved())
                .map(r -> ResponseEntity.ok(Map.of("result", r)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
