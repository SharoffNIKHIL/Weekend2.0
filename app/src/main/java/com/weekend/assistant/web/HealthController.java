package com.weekend.assistant.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness for Cloud Run and the entry node health check. Reveals nothing about data. */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
