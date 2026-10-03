package com.weekend.assistant.web;

import com.weekend.assistant.retention.DataService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** P6: export everything as JSON, or permanently delete everything with an exact confirmation phrase. */
@RestController
@RequestMapping("/api")
public class DataController {

    private final DataService data;

    public DataController(DataService data) {
        this.data = data;
    }

    public record DeleteAllRequest(String confirmation) {}

    @GetMapping("/export")
    public DataService.Export export() {
        return data.export("owner");
    }

    @PostMapping("/delete-all")
    public ResponseEntity<Map<String, Object>> deleteAll(@RequestBody DeleteAllRequest req) {
        if (!data.deleteAll(req.confirmation())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "deleted", false, "error", "Type the exact phrase: " + DataService.DELETE_CONFIRMATION));
        }
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
