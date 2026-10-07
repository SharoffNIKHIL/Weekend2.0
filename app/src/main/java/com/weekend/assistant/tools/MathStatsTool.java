package com.weekend.assistant.tools;

import com.weekend.assistant.math.MathEngine;
import com.weekend.assistant.math.MathStats;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Descriptive statistics for a list of numbers, locally. Read-only. */
@Component
public class MathStatsTool implements Tool {

    private final MathStats stats = new MathStats();

    @Override
    public String name() {
        return "math_stats";
    }

    @Override
    public String description() {
        return "Statistics for a list of numbers (comma or space separated): count, sum, mean, median, mode, min, max, range, "
                + "population and sample variance and standard deviation.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of("numbers", Schemas.string("e.g. 12, 15, 9, 22")), List.of("numbers"));
    }

    @Override
    public boolean writes() {
        return false;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        try {
            return ToolOutput.ok(stats.describe(Schemas.requireString(input, "numbers")).entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n")));
        } catch (MathEngine.MathException e) {
            return ToolOutput.error("Math error: " + e.getMessage());
        }
    }
}
