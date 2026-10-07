package com.weekend.assistant.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MathSolverAndStatsTest {

    private final MathSolver solver = new MathSolver();
    private final MathStats stats = new MathStats();

    private static String roots(MathSolver.Solution s) {
        return s.roots().stream().map(MathEngine::format).toList().toString();
    }

    @Test
    void solvesPolynomialsAndExponentials() {
        assertThat(roots(solver.solve("x^2 - 5x + 6 = 0", null, -1000, 1000))).isEqualTo("[2, 3]");
        assertThat(roots(solver.solve("x^3 = 8", null, -100, 100))).isEqualTo("[2]");
        assertThat(roots(solver.solve("x^2 = 2", "x", -10, 10))).isEqualTo("[-1.41421356237, 1.41421356237]");
        MathSolver.Solution r = solver.solve("1000*(1+r)^10 = 2000", null, 0, 1);
        assertThat(r.variable()).isEqualTo("r");
        assertThat(r.roots().get(0).doubleValue()).isCloseTo(0.0717735, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void handlesNoRootsPolesAndDomains() {
        assertThat(solver.solve("x^2 + 1 = 0", null, -100, 100).roots()).isEmpty();
        assertThat(solver.solve("1/x = 0", null, -10, 10).roots()).isEmpty();       // sign change at a pole is not a root
        assertThat(roots(solver.solve("ln(x) = 1", null, -5, 5))).isEqualTo("[2.71828182846]");
        assertThat(roots(solver.solve("sin(x) = 0", null, -1, 7))).isEqualTo("[0, 3.14159265359, 6.28318530718]");
    }

    @Test
    void validatesTheEquation() {
        assertThatThrownBy(() -> solver.solve("x + y = 1", null, -1, 1)).hasMessageContaining("more than one unknown");
        assertThatThrownBy(() -> solver.solve("2 + 2 = 4", null, -1, 1)).hasMessageContaining("no unknown");
        assertThatThrownBy(() -> solver.solve("x = 1 = 2", null, -1, 1)).hasMessageContaining("one '='");
        assertThatThrownBy(() -> solver.solve("x = 1", null, 5, 1)).hasMessageContaining("range");
        assertThat(roots(solver.solve("2y + 4 = 10", "y", -100, 100))).isEqualTo("[3]");
        assertThatThrownBy(() -> solver.solve("x + y = 10", "y", -100, 100)).hasMessageContaining("unknown name 'x'");
    }

    @Test
    void describesNumbersExactly() {
        Map<String, String> d = stats.describe("2, 4, 4, 4, 5, 5, 7, 9");
        assertThat(d).containsEntry("count", "8").containsEntry("sum", "40").containsEntry("mean", "5").containsEntry("median", "4.5")
                .containsEntry("mode", "4 (×3)").containsEntry("min", "2").containsEntry("max", "9").containsEntry("range", "7")
                .containsEntry("population variance", "4").containsEntry("population stdev", "2");
        assertThat(new BigDecimal(d.get("sample stdev")).doubleValue()).isCloseTo(2.13809, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(stats.describe("0.1 0.2").get("sum")).isEqualTo("0.3");
        assertThat(stats.describe("7")).containsEntry("mode", "none (all values distinct)").doesNotContainKey("sample stdev");
        assertThatThrownBy(() -> stats.describe("1, two, 3")).hasMessageContaining("not a number");
        assertThatThrownBy(() -> stats.describe(" ")).hasMessageContaining("at least one");
    }
}
