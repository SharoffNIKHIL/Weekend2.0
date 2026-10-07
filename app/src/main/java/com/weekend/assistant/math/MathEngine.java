package com.weekend.assistant.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

/**
 * Local, deterministic math (no network, no eval of code). Exact decimal arithmetic for + − × ÷ and integer powers
 * (34 significant digits), exact factorials and combinatorics with big integers, and double precision for
 * transcendental functions. Hard limits keep hostile input cheap: 1,000 characters, nesting depth 100, factorial ≤ 3,000,
 * integer exponents ≤ 10,000.
 */
public final class MathEngine {

    public static final MathContext MC = MathContext.DECIMAL128;
    static final int MAX_LENGTH = 1000;
    static final int MAX_DEPTH = 100;
    static final int MAX_FACTORIAL = 3000;
    static final int MAX_EXPONENT = 10_000;

    public static final Set<String> CONSTANTS = Set.of("pi", "e", "tau", "phi");
    public static final Set<String> FUNCTIONS = Set.of("sqrt", "cbrt", "abs", "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            "sinh", "cosh", "tanh", "ln", "log", "log2", "exp", "floor", "ceil", "round", "min", "max", "gcd", "lcm", "fact",
            "factorial", "ncr", "comb", "npr", "perm", "mod", "pow", "hypot", "deg", "rad");

    /** Thrown for any problem with the expression; the message is safe to show. */
    public static class MathException extends RuntimeException {
        public MathException(String message) {
            super(message);
        }
    }

    public BigDecimal evaluate(String expression) {
        return evaluate(expression, Map.of());
    }

    public BigDecimal evaluate(String expression, Map<String, BigDecimal> variables) {
        if (expression == null || expression.isBlank()) {
            throw new MathException("expression is empty");
        }
        if (expression.length() > MAX_LENGTH) {
            throw new MathException("expression is longer than " + MAX_LENGTH + " characters");
        }
        Parser p = new Parser(tokenize(expression), variables == null ? Map.of() : variables);
        BigDecimal v = p.expression(0);
        if (p.pos < p.tokens.size()) {
            throw new MathException("unexpected '" + p.tokens.get(p.pos).text + "'");
        }
        return v;
    }

    /** Display precision: hides the last-digit artefacts of 34-digit arithmetic (1/3*3 shows as 1). */
    static final MathContext DISPLAY = new MathContext(32, RoundingMode.HALF_EVEN);

    /**
     * Exact whole numbers up to 200 digits in full (e.g. 100!); otherwise 32 significant digits, plain for normal
     * magnitudes and scientific notation for very large or small ones; trailing zeros removed.
     */
    public static String format(BigDecimal v) {
        BigDecimal exact = v.stripTrailingZeros();
        if (exact.scale() <= 0 && exact.precision() <= 200) {
            return exact.toBigInteger().toString();
        }
        BigDecimal r = v.round(DISPLAY).stripTrailingZeros();
        if (r.signum() == 0) {
            return "0";
        }
        int exp = r.precision() - r.scale() - 1;
        return exp > 40 || exp < -12 ? r.toString() : r.toPlainString();
    }

    // ---------- tokenizer ----------
    private enum Kind { NUM, ID, OP, LPAREN, RPAREN, COMMA }

    private record Token(Kind kind, String text) {}

    private static List<Token> tokenize(String s) {
        String in = s.replace('×', '*').replace('÷', '/').replace('−', '-').replace("**", "^").replace("π", " pi ");
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < in.length()) {
            char c = in.charAt(i);
            if (Character.isWhitespace(c) || c == '_') {
                i++;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < in.length() && Character.isDigit(in.charAt(i + 1)))) {
                int j = i;
                while (j < in.length() && (Character.isDigit(in.charAt(j)) || in.charAt(j) == '.')) {
                    j++;
                }
                if (j < in.length() && (in.charAt(j) == 'e' || in.charAt(j) == 'E') && j + 1 < in.length()
                        && (Character.isDigit(in.charAt(j + 1)) || ((in.charAt(j + 1) == '-' || in.charAt(j + 1) == '+')
                        && j + 2 < in.length() && Character.isDigit(in.charAt(j + 2))))) {
                    j += 2;
                    while (j < in.length() && Character.isDigit(in.charAt(j))) {
                        j++;
                    }
                }
                out.add(new Token(Kind.NUM, in.substring(i, j)));
                i = j;
            } else if (Character.isLetter(c)) {
                int j = i;
                while (j < in.length() && (Character.isLetterOrDigit(in.charAt(j)))) {
                    j++;
                }
                String id = in.substring(i, j).toLowerCase(Locale.ROOT);
                out.add(new Token(Kind.ID, id));
                i = j;
            } else if ("+-*/^%!".indexOf(c) >= 0) {
                out.add(new Token(Kind.OP, String.valueOf(c)));
                i++;
            } else if (c == '(' || c == '[') {
                out.add(new Token(Kind.LPAREN, "("));
                i++;
            } else if (c == ')' || c == ']') {
                out.add(new Token(Kind.RPAREN, ")"));
                i++;
            } else if (c == ',') {
                out.add(new Token(Kind.COMMA, ","));
                i++;
            } else {
                throw new MathException("unsupported character '" + c + "'");
            }
        }
        return out;
    }

    // ---------- parser / evaluator ----------
    private static final class Parser {
        final List<Token> tokens;
        final Map<String, BigDecimal> vars;
        int pos;

        Parser(List<Token> tokens, Map<String, BigDecimal> vars) {
            this.tokens = tokens;
            this.vars = vars;
        }

        Token peek() {
            return pos < tokens.size() ? tokens.get(pos) : null;
        }

        boolean isOp(String op) {
            Token t = peek();
            return t != null && t.kind == Kind.OP && t.text.equals(op);
        }

        void guard(int depth) {
            if (depth > MAX_DEPTH) {
                throw new MathException("expression is nested too deeply");
            }
        }

        BigDecimal expression(int depth) {
            guard(depth);
            BigDecimal v = term(depth + 1);
            while (isOp("+") || isOp("-")) {
                String op = tokens.get(pos++).text;
                BigDecimal r = term(depth + 1);
                v = op.equals("+") ? v.add(r, MC) : v.subtract(r, MC);
            }
            return v;
        }

        BigDecimal term(int depth) {
            guard(depth);
            BigDecimal v = unary(depth + 1);
            while (true) {
                Token t = peek();
                if (isOp("*") || isOp("/") || isOp("%")) {
                    String op = tokens.get(pos++).text;
                    BigDecimal r = unary(depth + 1);
                    v = switch (op) {
                        case "*" -> v.multiply(r, MC);
                        case "/" -> divide(v, r);
                        default -> modulo(v, r);
                    };
                } else if (t != null && (t.kind == Kind.ID || t.kind == Kind.LPAREN)) {
                    v = v.multiply(unary(depth + 1), MC); // implicit multiplication: 2pi, 3(x+1), (a)(b)
                } else {
                    return v;
                }
            }
        }

        BigDecimal unary(int depth) {
            guard(depth);
            if (isOp("-")) {
                pos++;
                return unary(depth + 1).negate(MC);
            }
            if (isOp("+")) {
                pos++;
                return unary(depth + 1);
            }
            return power(depth + 1);
        }

        BigDecimal power(int depth) {
            guard(depth);
            BigDecimal base = postfix(depth + 1);
            if (isOp("^")) {
                pos++;
                BigDecimal exp = unary(depth + 1); // right-associative: 2^3^2 = 2^9
                return pow(base, exp);
            }
            return base;
        }

        BigDecimal postfix(int depth) {
            BigDecimal v = primary(depth + 1);
            while (isOp("!")) {
                pos++;
                v = new BigDecimal(factorial(v));
            }
            return v;
        }

        BigDecimal primary(int depth) {
            guard(depth);
            Token t = peek();
            if (t == null) {
                throw new MathException("expression ends too early");
            }
            pos++;
            switch (t.kind) {
                case NUM -> {
                    try {
                        return new BigDecimal(t.text, MC);
                    } catch (NumberFormatException e) {
                        throw new MathException("bad number '" + t.text + "'");
                    }
                }
                case LPAREN -> {
                    BigDecimal v = expression(depth + 1);
                    expect(Kind.RPAREN, ")");
                    return v;
                }
                case ID -> {
                    Token next = peek();
                    if (next != null && next.kind == Kind.LPAREN && FUNCTIONS.contains(t.text)) {
                        pos++;
                        List<BigDecimal> args = new ArrayList<>();
                        if (peek() != null && peek().kind != Kind.RPAREN) {
                            args.add(expression(depth + 1));
                            while (peek() != null && peek().kind == Kind.COMMA) {
                                pos++;
                                args.add(expression(depth + 1));
                            }
                        }
                        expect(Kind.RPAREN, ")");
                        return call(t.text, args);
                    }
                    if (vars.containsKey(t.text)) {
                        return vars.get(t.text);
                    }
                    return switch (t.text) {
                        case "pi" -> new BigDecimal("3.141592653589793238462643383279503");
                        case "e" -> new BigDecimal("2.718281828459045235360287471352662");
                        case "tau" -> new BigDecimal("6.283185307179586476925286766559006");
                        case "phi" -> new BigDecimal("1.618033988749894848204586834365638");
                        default -> throw new MathException(FUNCTIONS.contains(t.text)
                                ? t.text + " needs brackets, e.g. " + t.text + "(2)" : "unknown name '" + t.text + "'");
                    };
                }
                default -> throw new MathException("unexpected '" + t.text + "'");
            }
        }

        void expect(Kind kind, String text) {
            Token t = peek();
            if (t == null || t.kind != kind) {
                throw new MathException("missing '" + text + "'");
            }
            pos++;
        }
    }

    // ---------- operations ----------
    static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            throw new MathException("division by zero");
        }
        return a.divide(b, MC);
    }

    static BigDecimal modulo(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            throw new MathException("modulo by zero");
        }
        BigDecimal r = a.remainder(b, MC);
        return r.signum() != 0 && r.signum() != b.signum() ? r.add(b, MC) : r; // mathematical modulo
    }

    static BigDecimal pow(BigDecimal base, BigDecimal exp) {
        if (isInteger(exp) && exp.abs().compareTo(BigDecimal.valueOf(MAX_EXPONENT)) <= 0) {
            int n = exp.intValueExact();
            if (n < 0 && base.signum() == 0) {
                throw new MathException("division by zero");
            }
            return base.pow(n, MC);
        }
        double r = Math.pow(base.doubleValue(), exp.doubleValue());
        return fromDouble(r, "power");
    }

    static BigInteger factorial(BigDecimal v) {
        if (!isInteger(v) || v.signum() < 0) {
            throw new MathException("factorial needs a whole number ≥ 0");
        }
        if (v.compareTo(BigDecimal.valueOf(MAX_FACTORIAL)) > 0) {
            throw new MathException("factorial is limited to " + MAX_FACTORIAL + "!");
        }
        BigInteger r = BigInteger.ONE;
        for (int i = 2; i <= v.intValueExact(); i++) {
            r = r.multiply(BigInteger.valueOf(i));
        }
        return r;
    }

    static boolean isInteger(BigDecimal v) {
        return v.signum() == 0 || v.stripTrailingZeros().scale() <= 0;
    }

    static BigInteger integer(BigDecimal v, String fn) {
        if (!isInteger(v)) {
            throw new MathException(fn + " needs whole numbers");
        }
        if (v.abs().compareTo(new BigDecimal("1e1000")) > 0) {
            throw new MathException(fn + ": number too large");
        }
        return v.toBigIntegerExact();
    }

    static BigDecimal fromDouble(double d, String fn) {
        if (Double.isNaN(d)) {
            throw new MathException(fn + " is undefined here (complex results are not supported)");
        }
        if (Double.isInfinite(d)) {
            throw new MathException(fn + " overflows");
        }
        return new BigDecimal(d, MathContext.DECIMAL64);
    }

    private static BigDecimal call(String fn, List<BigDecimal> a) {
        int n = a.size();
        switch (fn) {
            case "min", "max", "gcd", "lcm" -> {
                if (n == 0) {
                    throw new MathException(fn + " needs at least one value");
                }
            }
            case "atan2", "ncr", "comb", "npr", "perm", "mod", "pow", "hypot" -> {
                if (n != 2) {
                    throw new MathException(fn + " needs 2 values");
                }
            }
            case "log", "round" -> {
                if (n < 1 || n > 2) {
                    throw new MathException(fn + " needs 1 or 2 values");
                }
            }
            default -> {
                if (n != 1) {
                    throw new MathException(fn + " needs 1 value");
                }
            }
        }
        BigDecimal x = a.get(0);
        return switch (fn) {
            case "sqrt" -> {
                if (x.signum() < 0) {
                    throw new MathException("sqrt of a negative number (complex results are not supported)");
                }
                yield x.sqrt(MC);
            }
            case "cbrt" -> fromDouble(Math.cbrt(x.doubleValue()), fn);
            case "abs" -> x.abs();
            case "sin" -> d(Math::sin, x, fn);
            case "cos" -> d(Math::cos, x, fn);
            case "tan" -> d(Math::tan, x, fn);
            case "asin" -> d(Math::asin, x, fn);
            case "acos" -> d(Math::acos, x, fn);
            case "atan" -> d(Math::atan, x, fn);
            case "sinh" -> d(Math::sinh, x, fn);
            case "cosh" -> d(Math::cosh, x, fn);
            case "tanh" -> d(Math::tanh, x, fn);
            case "exp" -> d(Math::exp, x, fn);
            case "deg" -> d(Math::toDegrees, x, fn);
            case "rad" -> d(Math::toRadians, x, fn);
            case "ln" -> positive(x, fn) ? d(Math::log, x, fn) : null;
            case "log2" -> positive(x, fn) ? d(v -> Math.log(v) / Math.log(2), x, fn) : null;
            case "log" -> {
                positive(x, fn);
                if (n == 2) {
                    positive(a.get(1), fn);
                    yield fromDouble(Math.log(x.doubleValue()) / Math.log(a.get(1).doubleValue()), fn);
                }
                yield d(Math::log10, x, fn);
            }
            case "atan2" -> fromDouble(Math.atan2(x.doubleValue(), a.get(1).doubleValue()), fn);
            case "hypot" -> x.pow(2, MC).add(a.get(1).pow(2, MC), MC).sqrt(MC);
            case "floor" -> x.setScale(0, RoundingMode.FLOOR);
            case "ceil" -> x.setScale(0, RoundingMode.CEILING);
            case "round" -> x.setScale(n == 2 ? integer(a.get(1), fn).intValue() : 0, RoundingMode.HALF_UP);
            case "min" -> a.stream().min(BigDecimal::compareTo).orElseThrow();
            case "max" -> a.stream().max(BigDecimal::compareTo).orElseThrow();
            case "gcd" -> new BigDecimal(a.stream().map(v -> integer(v, fn)).reduce(BigInteger.ZERO, BigInteger::gcd));
            case "lcm" -> new BigDecimal(a.stream().map(v -> integer(v, fn).abs()).reduce(BigInteger.ONE,
                    (p, q) -> p.signum() == 0 || q.signum() == 0 ? BigInteger.ZERO : p.divide(p.gcd(q)).multiply(q)));
            case "fact", "factorial" -> new BigDecimal(factorial(x));
            case "ncr", "comb", "npr", "perm" -> {
                BigInteger nn = integer(x, fn);
                BigInteger k = integer(a.get(1), fn);
                if (nn.signum() < 0 || k.signum() < 0 || k.compareTo(nn) > 0 || nn.compareTo(BigInteger.valueOf(100_000)) > 0) {
                    throw new MathException(fn + " needs 0 ≤ k ≤ n ≤ 100000");
                }
                BigInteger r = BigInteger.ONE;
                int kk = k.intValue();
                int nnn = nn.intValue();
                boolean choose = fn.equals("ncr") || fn.equals("comb");
                int kEff = choose ? Math.min(kk, nnn - kk) : kk;
                for (int i = 0; i < kEff; i++) {
                    r = r.multiply(BigInteger.valueOf(nnn - i));
                    if (choose) {
                        r = r.divide(BigInteger.valueOf(i + 1));
                    }
                }
                yield new BigDecimal(r);
            }
            case "mod" -> modulo(x, a.get(1));
            case "pow" -> pow(x, a.get(1));
            default -> throw new MathException("unknown function " + fn);
        };
    }

    private static boolean positive(BigDecimal x, String fn) {
        if (x.signum() <= 0) {
            throw new MathException(fn + " needs a positive number");
        }
        return true;
    }

    private static BigDecimal d(DoubleUnaryOperator f, BigDecimal x, String fn) {
        return fromDouble(f.applyAsDouble(x.doubleValue()), fn);
    }
}
