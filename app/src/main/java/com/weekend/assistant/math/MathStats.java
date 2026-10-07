package com.weekend.assistant.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Descriptive statistics with exact decimal sums: count, sum, mean, median, mode, min, max, range, variance, stdev. */
public final class MathStats {

    static final int MAX_VALUES = 10_000;
    private static final MathContext MC = MathEngine.MC;

    public Map<String, String> describe(String numbers) {
        List<BigDecimal> xs = parse(numbers);
        int n = xs.size();
        List<BigDecimal> sorted = new ArrayList<>(xs);
        sorted.sort(BigDecimal::compareTo);
        BigDecimal sum = xs.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), MC);
        BigDecimal median = n % 2 == 1 ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2), MC).divide(BigDecimal.valueOf(2), MC);
        BigDecimal ss = xs.stream().map(x -> x.subtract(mean, MC).pow(2, MC)).reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
        Map<String, String> out = new LinkedHashMap<>();
        out.put("count", String.valueOf(n));
        out.put("sum", MathEngine.format(sum));
        out.put("mean", MathEngine.format(mean));
        out.put("median", MathEngine.format(median));
        out.put("mode", mode(sorted));
        out.put("min", MathEngine.format(sorted.get(0)));
        out.put("max", MathEngine.format(sorted.get(n - 1)));
        out.put("range", MathEngine.format(sorted.get(n - 1).subtract(sorted.get(0), MC)));
        BigDecimal popVar = ss.divide(BigDecimal.valueOf(n), MC);
        out.put("population variance", MathEngine.format(popVar));
        out.put("population stdev", MathEngine.format(popVar.sqrt(MC)));
        if (n > 1) {
            BigDecimal var = ss.divide(BigDecimal.valueOf(n - 1L), MC);
            out.put("sample variance", MathEngine.format(var));
            out.put("sample stdev", MathEngine.format(var.sqrt(MC)));
        }
        return out;
    }

    static List<BigDecimal> parse(String numbers) {
        if (numbers == null || numbers.isBlank()) {
            throw new MathEngine.MathException("give at least one number");
        }
        List<BigDecimal> xs = new ArrayList<>();
        for (String part : numbers.split("[,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            if (xs.size() >= MAX_VALUES) {
                throw new MathEngine.MathException("at most " + MAX_VALUES + " numbers");
            }
            try {
                xs.add(new BigDecimal(part.strip(), MC));
            } catch (NumberFormatException e) {
                throw new MathEngine.MathException("not a number: '" + (part.length() > 20 ? part.substring(0, 20) + "…" : part) + "'");
            }
        }
        if (xs.isEmpty()) {
            throw new MathEngine.MathException("give at least one number");
        }
        return xs;
    }

    private static String mode(List<BigDecimal> sorted) {
        Map<BigDecimal, Integer> counts = new LinkedHashMap<>();
        sorted.forEach(x -> counts.merge(x.stripTrailingZeros(), 1, Integer::sum));
        int best = counts.values().stream().max(Integer::compare).orElse(0);
        if (best <= 1) {
            return "none (all values distinct)";
        }
        return counts.entrySet().stream().filter(e -> e.getValue() == best).map(e -> MathEngine.format(e.getKey()))
                .reduce((a, b) -> a + ", " + b).orElse("") + " (×" + best + ")";
    }
}
