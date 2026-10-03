package com.weekend.assistant.tools;

import com.weekend.assistant.config.WeekendProperties;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Current date and time in the owner's time zone (IST). */
@Component
public class CurrentTimeTool implements Tool {

    private final Clock clock;
    private final WeekendProperties props;

    public CurrentTimeTool(Clock clock, WeekendProperties props) {
        this.clock = clock;
        this.props = props;
    }

    @Override
    public String name() {
        return "current_time";
    }

    @Override
    public String description() {
        return "Returns the current date, time and weekday in the owner's time zone.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of(), List.of());
    }

    @Override
    public boolean writes() {
        return false;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(props.zone()));
        return ToolOutput.ok(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm z")));
    }
}
