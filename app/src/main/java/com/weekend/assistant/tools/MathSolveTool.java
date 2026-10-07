package com.weekend.assistant.tools;

import com.weekend.assistant.math.MathEngine;
import com.weekend.assistant.math.MathSolver;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Solves an equation in one unknown numerically, locally. Read-only. */
@Component
public class MathSolveTool implements Tool {

    private final MathSolver solver = new MathSolver();

    @Override
    public String name() {
        return "math_solve";
    }

    @Override
    public String description() {
        return "Find the real solutions of an equation in one unknown, e.g. \"x^2 - 5x + 6 = 0\" or \"1000*(1+r)^10 = 2000\". "
                + "Searches the range from..to (default -1000..1000). Local and exact to about 12 significant digits.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of(
                        "equation", Schemas.string("Equation with one unknown, or an expression meaning '= 0'"),
                        "variable", Schemas.string("Optional: the unknown's name if there are several names"),
                        "from", Schemas.string("Optional lower bound, default -1000"),
                        "to", Schemas.string("Optional upper bound, default 1000")),
                List.of("equation"));
    }

    @Override
    public boolean writes() {
        return false;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        try {
            double from = bound(Schemas.optionalString(input, "from"), -1000);
            double to = bound(Schemas.optionalString(input, "to"), 1000);
            MathSolver.Solution s = solver.solve(Schemas.requireString(input, "equation"), Schemas.optionalString(input, "variable"), from, to);
            if (s.roots().isEmpty()) {
                return ToolOutput.ok("No real solution for " + s.variable() + " found between " + s.from() + " and " + s.to() + ".");
            }
            return ToolOutput.ok(s.variable() + " = " + s.roots().stream().map(MathEngine::format).collect(Collectors.joining(", "))
                    + "  (searched " + s.from() + " to " + s.to() + ")");
        } catch (MathEngine.MathException e) {
            return ToolOutput.error("Math error: " + e.getMessage());
        }
    }

    private static double bound(String v, double fallback) {
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return new MathEngine().evaluate(v).doubleValue();
    }
}
