package com.weekend.assistant.web;

import com.weekend.assistant.config.WeekendProperties;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-secret runtime settings for the Settings screen: which model runs, where prompts are processed (P7),
 * retention (P5) and the daily cost cap. Behind the owner session like the rest of /api. No project IDs or keys.
 */
@RestController
@RequestMapping("/api")
public class InfoController {

    private final WeekendProperties props;

    public InfoController(WeekendProperties props) {
        this.props = props;
    }

    public record Info(
            String provider,
            String modelDefault,
            String modelStrong,
            String processingLocation,
            boolean dataLeavesIndia,
            BigDecimal dailyCostCapUsd,
            Map<String, Long> retentionDays,
            String ownerTimezone) {}

    @GetMapping("/info")
    public Info info() {
        WeekendProperties.Llm llm = props.llm();
        boolean vertex = "vertex".equals(llm.provider());
        String location = vertex ? llm.vertexLocation() : "this device (offline model)";
        WeekendProperties.Retention r = props.retention();
        return new Info(
                llm.provider(),
                llm.modelDefault(),
                llm.modelStrong(),
                location,
                vertex && !llm.vertexLocation().startsWith("asia-south"),
                props.agent().dailyCostCapUsd(),
                Map.of("messages", r.messages().toDays(),
                        "unpinnedMemories", r.unpinnedMemories().toDays(),
                        "toolCalls", r.toolCalls().toDays(),
                        "auditLog", r.auditLog().toDays()),
                props.ownerTimezone());
    }
}
