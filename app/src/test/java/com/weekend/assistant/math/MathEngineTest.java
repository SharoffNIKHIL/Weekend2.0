package com.weekend.assistant.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MathEngineTest {

    private final MathEngine m = new MathEngine();

    private String eval(String expr) {
        return MathEngine.format(m.evaluate(expr));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = '|', value = {
            "1+2*3 | 7",
            "(1+2)*3 | 9",
            "2^3^2 | 512",
            "-2^2 | -4",
            "(-2)^2 | 4",
            "2^-2 | 0.25",
            "0.1+0.2 | 0.3",
            "1/3*3 | 1",
            "10 % 3 | 1",
            "-7 % 3 | 2",
            "mod(-7, 3) | 2",
            "5! | 120",
            "3!^2 | 36",
            "ncr(52,5) | 2598960",
            "npr(5,2) | 20",
            "gcd(84, 36, 60) | 12",
            "lcm(4, 6, 10) | 60",
            "sqrt(144) | 12",
            "hypot(3,4) | 5",
            "abs(-3.5) | 3.5",
            "floor(-2.5) | -3",
            "ceil(2.1) | 3",
            "round(2.5) | 3",
            "round(3.14159, 2) | 3.14",
            "min(4, -1, 9) | -1",
            "max(4, -1, 9) | 9",
            "2pi/tau | 1",
            "3(2+1) | 9",
            "(1+1)(2+2) | 8",
            "2 × 3 ÷ 4 | 1.5",
            "1e3 + 2.5E-1 | 1000.25",
            "log(1000) | 3",
            "log(8, 2) | 3",
            "log2(1024) | 10",
            "ln(e) | 1",
            "pow(2, 10) | 1024",
            "2**10 | 1024",
            "[1+2]*3 | 9",
            "1000000*1000000 | 1000000000000"})
    void evaluatesExpressions(String expr, String expected) {
        assertThat(eval(expr)).isEqualTo(expected);
    }

    @Test
    void transcendentalFunctionsAreCloseToDoublePrecision() {
        assertThat(m.evaluate("sin(pi/6)").doubleValue()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(m.evaluate("cos(0)").doubleValue()).isEqualTo(1.0);
        assertThat(m.evaluate("deg(pi)").doubleValue()).isCloseTo(180.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(m.evaluate("exp(1)").doubleValue()).isCloseTo(Math.E, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(m.evaluate("2^0.5").doubleValue()).isCloseTo(Math.sqrt(2), org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void exactBigResults() {
        assertThat(eval("25!")).isEqualTo("15511210043330985984000000");
        assertThat(eval("100!").length()).isGreaterThan(30);           // shown in scientific notation, not truncated to a double
        assertThat(m.evaluate("100!").toBigInteger().toString()).startsWith("93326215443944152681699238856266700490715968264381621468592963895217599993229915608941463976156518286253697920827223758251185210916864");
        assertThat(eval("2^100")).isEqualTo("1267650600228229401496703205376");
    }

    @Test
    void variablesWin() {
        assertThat(m.evaluate("x^2 + y", Map.of("x", new BigDecimal("3"), "y", BigDecimal.ONE))).isEqualByComparingTo("10");
        assertThat(m.evaluate("e*2", Map.of("e", BigDecimal.TEN))).isEqualByComparingTo("20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1/0", "5 % 0", "sqrt(-1)", "ln(0)", "log(-2)", "3.5!", "(-1)!", "3001!", "2^20000.5",
            "foo + 1", "sin 3", "(1+2", "1+", "1 2", "2 $ 3", "", "ncr(3,5)", "min()", "pow(2)", "asin(2)"})
    void rejectsBadInputWithAReadableError(String expr) {
        assertThatThrownBy(() -> m.evaluate(expr)).isInstanceOf(MathEngine.MathException.class);
    }

    @Test
    void limitsKeepHostileInputCheap() {
        assertThatThrownBy(() -> m.evaluate("1+".repeat(600) + "1")).hasMessageContaining("longer than");
        assertThatThrownBy(() -> m.evaluate("(".repeat(300) + "1" + ")".repeat(300))).hasMessageContaining("nested too deeply");
        assertThatThrownBy(() -> m.evaluate("2^20001")).isInstanceOf(MathEngine.MathException.class); // falls to double → overflow
    }
}
