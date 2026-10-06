package com.weekend.assistant.web;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UI preview only ({@code ui} profile): a tiny "other agent" on loopback that speaks weekend-agent/1, so connecting,
 * testing and delegating to a remote agent can be tried end to end without anything leaving the Mac.
 */
@RestController
@Profile("ui")
@RequestMapping("/demo-agent")
public class DemoAgentController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/message")
    public Map<String, String> message(@RequestBody Map<String, Object> body) {
        String text = String.valueOf(body.getOrDefault("message", ""));
        String preview = text.length() <= 80 ? text : text.substring(0, 77) + "...";
        return Map.of("reply", "Demo agent here (running on this Mac only). I received " + text.length()
                + " characters: \"" + preview + "\"");
    }
}
