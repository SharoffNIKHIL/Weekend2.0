package com.weekend.assistant.tools;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.reminder.ReminderService;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Creates a one-time reminder. Writes = true, so the agent asks the owner first. */
@Component
public class ReminderCreateTool implements Tool {

    private final ReminderService reminders;
    private final WeekendProperties props;

    public ReminderCreateTool(ReminderService reminders, WeekendProperties props) {
        this.reminders = reminders;
        this.props = props;
    }

    @Override
    public String name() {
        return "reminder_create";
    }

    @Override
    public String description() {
        return "Create a one-time reminder. due_local is an ISO local date-time in the owner's time zone, e.g. 2026-10-15T09:00.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of(
                        "text", Schemas.string("What to remind about"),
                        "due_local", Schemas.string("ISO-8601 local date-time in the owner's time zone")),
                List.of("text", "due_local"));
    }

    @Override
    public boolean writes() {
        return true;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        String text = Schemas.requireString(input, "text");
        LocalDateTime local;
        try {
            local = LocalDateTime.parse(Schemas.requireString(input, "due_local"));
        } catch (DateTimeParseException e) {
            return ToolOutput.error("due_local must look like 2026-10-15T09:00");
        }
        return reminders.create(text, local.atZone(props.zone()).toInstant())
                .map(r -> ToolOutput.ok("Reminder " + r.id() + " set for " + local))
                .orElseGet(() -> ToolOutput.error("The reminder time is in the past."));
    }
}
