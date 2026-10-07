package com.weekend.assistant.tools;

import com.weekend.assistant.math.MathEngine;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Evaluates a math expression locally and exactly (no network). Read-only. */
@Component
public class MathEvaluateTool implements Tool {

    private final MathEngine engine = new MathEngine();

    @Override
    public String name() {
        return "math_evaluate";
    }

    @Override
    public String description() {
        return "Calculate a math expression exactly, locally. Supports + - * / ^ % !, brackets, implicit multiplication (2pi), "
                + "sqrt cbrt abs sin cos tan asin acos atan atan2 sinh cosh tanh ln log(x[,base]) log2 exp floor ceil round(x[,dp]) "
                + "min max gcd lcm fact ncr npr mod pow hypot deg rad, constants pi e tau phi. Angles are in radians. "
                + "Optional variables like \"x=2, y=3\". Use it for every non-trivial calculation instead of mental math.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Schemas.object(Map.of(
                        "expression", Schemas.string("e.g. (1+2)^10 / 7, 25! , ncr(52,5), sqrt(2)*pi"),
                        "variables", Schemas.string("Optional, e.g. x=2, rate=0.07")),
                List.of("expression"));
    }

    @Override
    public boolean writes() {
        return false;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input, ToolContext context) {
        String expr = Schemas.requireString(input, "expression");
        try {
            Map<String, BigDecimal> vars = variables(Schemas.optionalString(input, "variables"));
            return ToolOutput.ok(expr + " = " + MathEngine.format(engine.evaluate(expr, vars)));
        } catch (MathEngine.MathException e) {
            return ToolOutput.error("Math error: " + e.getMessage());
        }
    }

    static Map<String, BigDecimal> variables(String spec) {
        Map<String, BigDecimal> vars = new LinkedHashMap<>();
        if (spec == null || spec.isBlank()) {
            return vars;
        }
        MathEngine engine = new MathEngine();
        for (String part : spec.split("[,;]")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2 || !kv[0].strip().matches("[A-Za-z][A-Za-z0-9]*")) {
                throw new MathEngine.MathException("variables must look like x=2, y=3");
            }
            vars.put(kv[0].strip().toLowerCase(java.util.Locale.ROOT), engine.evaluate(kv[1], vars));
        }
        return vars;
    }
}
