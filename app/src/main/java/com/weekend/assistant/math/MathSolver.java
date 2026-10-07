package com.weekend.assistant.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds real roots of an equation in one unknown ("x^2 - 5x + 6 = 0", "2^x = 10", "sin(x) = 0.5") by scanning a range
 * for sign changes and refining each with bisection. Numeric, so it reports roots it finds in the range, not proofs.
 */
public final class MathSolver {

    static final int SAMPLES = 4000;
    static final int MAX_ROOTS = 20;

    private final MathEngine engine = new MathEngine();

    public record Solution(String variable, List<BigDecimal> roots, double from, double to) {}

    public Solution solve(String equation, String variable, double from, double to) {
        if (equation == null || equation.isBlank()) {
            throw new MathEngine.MathException("equation is empty");
        }
        if (!(from < to) || to - from > 1e9) {
            throw new MathEngine.MathException("range must satisfy from < to and be at most 1e9 wide");
        }
        String[] sides = equation.split("=");
        if (sides.length > 2) {
            throw new MathEngine.MathException("use at most one '='");
        }
        String f = sides.length == 2 ? "(" + sides[0] + ")-(" + sides[1] + ")" : equation;
        String var = variable == null || variable.isBlank() ? guessVariable(f) : variable.strip().toLowerCase(java.util.Locale.ROOT);
        engine.evaluate(f, Map.of(var, BigDecimal.ONE)); // validates syntax and names early

        List<BigDecimal> roots = new ArrayList<>();
        double step = (to - from) / SAMPLES;
        double prevX = from;
        Double prevY = value(f, var, from);
        for (int i = 1; i <= SAMPLES && roots.size() < MAX_ROOTS; i++) {
            double x = from + i * step;
            Double y = value(f, var, x);
            if (prevY != null && prevY == 0.0) {
                add(roots, prevX);
            } else if (prevY != null && y != null && Math.signum(prevY) != Math.signum(y) && y != 0.0) {
                Double root = bisect(f, var, prevX, x, prevY);
                if (root != null) {
                    add(roots, root);
                }
            }
            prevX = x;
            prevY = y;
        }
        if (prevY != null && prevY == 0.0) {
            add(roots, prevX);
        }
        return new Solution(var, roots, from, to);
    }

    /** The single unknown in the expression (not a function or constant). */
    static String guessVariable(String expr) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = Pattern.compile("[A-Za-z][A-Za-z0-9]*").matcher(expr);
        while (m.find()) {
            String n = m.group().toLowerCase(java.util.Locale.ROOT);
            if (!MathEngine.FUNCTIONS.contains(n) && !MathEngine.CONSTANTS.contains(n)) {
                names.add(n);
            }
        }
        if (names.size() != 1) {
            throw new MathEngine.MathException(names.isEmpty() ? "no unknown to solve for" : "more than one unknown " + names + "; say which one");
        }
        return names.iterator().next();
    }

    private Double value(String f, String var, double x) {
        try {
            return engine.evaluate(f, Map.of(var, new BigDecimal(x, MathContext.DECIMAL64))).doubleValue();
        } catch (MathEngine.MathException | ArithmeticException e) {
            return null; // outside the domain at this point (e.g. log of a negative)
        }
    }

    private Double bisect(String f, String var, double a, double b, double fa) {
        double lo = a;
        double hi = b;
        double flo = fa;
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) / 2;
            Double fm = value(f, var, mid);
            if (fm == null) {
                return null;
            }
            if (fm == 0.0 || (hi - lo) < 1e-13 * Math.max(1, Math.abs(mid))) {
                return mid;
            }
            if (Math.signum(fm) == Math.signum(flo)) {
                lo = mid;
                flo = fm;
            } else {
                hi = mid;
            }
        }
        double mid = (lo + hi) / 2;
        Double fm = value(f, var, mid);
        // a sign change across a pole (e.g. 1/x) is not a root: the value there must be small
        return fm != null && Math.abs(fm) < 1e-6 * Math.max(1, Math.abs(mid)) ? mid : null;
    }

    private static void add(List<BigDecimal> roots, double x) {
        BigDecimal r = new BigDecimal(x, new MathContext(12)).stripTrailingZeros();
        if (r.abs().compareTo(new BigDecimal("1e-10")) < 0) {
            r = BigDecimal.ZERO;
        }
        for (BigDecimal existing : roots) {
            if (existing.subtract(r).abs().doubleValue() < 1e-7 * Math.max(1, Math.abs(x))) {
                return;
            }
        }
        roots.add(r);
    }
}
