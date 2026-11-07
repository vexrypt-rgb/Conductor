package conductor.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The part of Bedrock's Molang that animation files use for computed keyframes: numbers, {@code + - * /}, comparisons,
 * {@code && || !}, {@code ?:}, parentheses, {@code math.*} functions (angles in degrees, as in Molang) and a few
 * queries. Expressions are parsed once, when the file loads; an unknown name is an error then, not a silent zero
 * every frame.
 */
public final class Molang {

    /** The values queries read. */
    public interface Context {
        /** Seconds since the animation started. */
        double animTime();

        /** Seconds the entity has existed. */
        double lifeTime();

        /** Blocks walked (vanilla's limb swing position). */
        double distanceMoved();

        /** 0 to about 1: how fast it walks (vanilla's limb swing amount). */
        double moveSpeed();
    }

    /** A parsed expression. */
    @FunctionalInterface
    public interface Expr {
        double eval(Context c);
    }

    private static final int MAX_LENGTH = 4096;
    private static final int MAX_DEPTH = 64;

    private final String s;
    private int i;
    private int depth;

    private Molang(String s) {
        this.s = s;
    }

    /** A constant. */
    public static Expr constant(double v) {
        return c -> v;
    }

    /** Parse an expression; throws {@link IllegalArgumentException} saying what is wrong. */
    public static Expr parse(String text) {
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Molang expression longer than " + MAX_LENGTH + " characters");
        }
        String src = text.toLowerCase(Locale.ROOT).trim();
        while (src.endsWith(";")) {
            src = src.substring(0, src.length() - 1).trim();
        }
        if (src.isEmpty()) {
            return constant(0);
        }
        Molang p = new Molang(src);
        Expr e = p.ternary();
        p.space();
        if (p.i != src.length()) {
            throw p.error("unexpected '" + src.charAt(p.i) + "'");
        }
        return e;
    }

    private Expr ternary() {
        enter();
        Expr cond = or();
        space();
        if (eat("?")) {
            Expr a = ternary();
            space();
            if (!eat(":")) {
                throw error("expected ':'");
            }
            Expr b = ternary();
            depth--;
            return c -> cond.eval(c) != 0 ? a.eval(c) : b.eval(c);
        }
        depth--;
        return cond;
    }

    private Expr or() {
        Expr left = and();
        while (true) {
            space();
            if (eat("||")) {
                Expr l = left;
                Expr r = and();
                left = c -> l.eval(c) != 0 || r.eval(c) != 0 ? 1 : 0;
            } else {
                return left;
            }
        }
    }

    private Expr and() {
        Expr left = compare();
        while (true) {
            space();
            if (eat("&&")) {
                Expr l = left;
                Expr r = compare();
                left = c -> l.eval(c) != 0 && r.eval(c) != 0 ? 1 : 0;
            } else {
                return left;
            }
        }
    }

    private Expr compare() {
        Expr left = sum();
        while (true) {
            space();
            Expr l = left;
            if (eat("<=")) {
                Expr r = sum();
                left = c -> l.eval(c) <= r.eval(c) ? 1 : 0;
            } else if (eat(">=")) {
                Expr r = sum();
                left = c -> l.eval(c) >= r.eval(c) ? 1 : 0;
            } else if (eat("==")) {
                Expr r = sum();
                left = c -> l.eval(c) == r.eval(c) ? 1 : 0;
            } else if (eat("!=")) {
                Expr r = sum();
                left = c -> l.eval(c) != r.eval(c) ? 1 : 0;
            } else if (eat("<")) {
                Expr r = sum();
                left = c -> l.eval(c) < r.eval(c) ? 1 : 0;
            } else if (eat(">")) {
                Expr r = sum();
                left = c -> l.eval(c) > r.eval(c) ? 1 : 0;
            } else {
                return left;
            }
        }
    }

    private Expr sum() {
        Expr left = product();
        while (true) {
            space();
            Expr l = left;
            if (eat("+")) {
                Expr r = product();
                left = c -> l.eval(c) + r.eval(c);
            } else if (eat("-")) {
                Expr r = product();
                left = c -> l.eval(c) - r.eval(c);
            } else {
                return left;
            }
        }
    }

    private Expr product() {
        Expr left = unary();
        while (true) {
            space();
            Expr l = left;
            if (eat("*")) {
                Expr r = unary();
                left = c -> l.eval(c) * r.eval(c);
            } else if (eat("/")) {
                Expr r = unary();
                left = c -> {
                    double d = r.eval(c);
                    return d == 0 ? 0 : l.eval(c) / d;
                };
            } else {
                return left;
            }
        }
    }

    private Expr unary() {
        space();
        if (eat("-")) {
            enter();
            Expr e = unary();
            depth--;
            return c -> -e.eval(c);
        }
        if (eat("+")) {
            return unary();
        }
        if (peek('!') && !s.startsWith("!=", i)) {
            i++;
            enter();
            Expr e = unary();
            depth--;
            return c -> e.eval(c) == 0 ? 1 : 0;
        }
        return primary();
    }

    private Expr primary() {
        space();
        if (i >= s.length()) {
            throw error("unexpected end");
        }
        char ch = s.charAt(i);
        if (ch == '(') {
            i++;
            Expr e = ternary();
            space();
            if (!eat(")")) {
                throw error("expected ')'");
            }
            return e;
        }
        if ((ch >= '0' && ch <= '9') || ch == '.') {
            int start = i;
            while (i < s.length() && ((s.charAt(i) >= '0' && s.charAt(i) <= '9') || s.charAt(i) == '.')) {
                i++;
            }
            double v;
            try {
                v = Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw error("bad number");
            }
            if (peek('f')) {
                i++;
            }
            return constant(v);
        }
        if (Character.isLetter(ch) || ch == '_') {
            int start = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) {
                i++;
            }
            String name = s.substring(start, i);
            space();
            if (peek('(')) {
                i++;
                List<Expr> args = new ArrayList<>();
                space();
                if (!eat(")")) {
                    do {
                        args.add(ternary());
                        space();
                    } while (eat(","));
                    if (!eat(")")) {
                        throw error("expected ')' after the arguments of " + name);
                    }
                }
                return function(name, args);
            }
            return name(name);
        }
        throw error("unexpected '" + ch + "'");
    }

    private Expr name(String name) {
        String q = name.startsWith("q.") ? "query." + name.substring(2) : name;
        switch (q) {
            case "math.pi":
                return constant(Math.PI);
            case "query.anim_time":
                return Context::animTime;
            case "query.life_time":
                return Context::lifeTime;
            case "query.modified_distance_moved":
                return Context::distanceMoved;
            case "query.modified_move_speed":
                return Context::moveSpeed;
            case "true":
                return constant(1);
            case "false":
                return constant(0);
            default:
                throw error("unknown name '" + name + "' (supported: query.anim_time, query.life_time, "
                    + "query.modified_distance_moved, query.modified_move_speed, math.pi)");
        }
    }

    private Expr function(String name, List<Expr> a) {
        String f = name.startsWith("math.") ? name.substring(5) : null;
        if (f == null) {
            throw error("unknown function '" + name + "'");
        }
        switch (f) {
            case "sin":
                return one(f, a, v -> Math.sin(Math.toRadians(v)));
            case "cos":
                return one(f, a, v -> Math.cos(Math.toRadians(v)));
            case "asin":
                return one(f, a, v -> Math.toDegrees(Math.asin(v)));
            case "acos":
                return one(f, a, v -> Math.toDegrees(Math.acos(v)));
            case "atan":
                return one(f, a, v -> Math.toDegrees(Math.atan(v)));
            case "abs":
                return one(f, a, Math::abs);
            case "sqrt":
                return one(f, a, Math::sqrt);
            case "floor":
                return one(f, a, Math::floor);
            case "ceil":
                return one(f, a, Math::ceil);
            case "round":
                return one(f, a, v -> (double) Math.round(v));
            case "trunc":
                return one(f, a, v -> v < 0 ? Math.ceil(v) : Math.floor(v));
            case "exp":
                return one(f, a, Math::exp);
            case "ln":
                return one(f, a, Math::log);
            case "atan2":
                return two(f, a, (y, x) -> Math.toDegrees(Math.atan2(y, x)));
            case "min":
                return two(f, a, Math::min);
            case "max":
                return two(f, a, Math::max);
            case "pow":
                return two(f, a, Math::pow);
            case "mod":
                return two(f, a, (x, y) -> y == 0 ? 0 : x % y);
            case "clamp": {
                arity(f, a, 3);
                Expr v = a.get(0), lo = a.get(1), hi = a.get(2);
                return c -> Math.max(lo.eval(c), Math.min(hi.eval(c), v.eval(c)));
            }
            case "lerp": {
                arity(f, a, 3);
                Expr x = a.get(0), y = a.get(1), t = a.get(2);
                return c -> {
                    double from = x.eval(c);
                    return from + (y.eval(c) - from) * t.eval(c);
                };
            }
            default:
                throw error("unknown function '" + name + "'");
        }
    }

    private Expr one(String f, List<Expr> a, java.util.function.DoubleUnaryOperator op) {
        arity(f, a, 1);
        Expr x = a.get(0);
        return c -> op.applyAsDouble(x.eval(c));
    }

    private Expr two(String f, List<Expr> a, java.util.function.DoubleBinaryOperator op) {
        arity(f, a, 2);
        Expr x = a.get(0), y = a.get(1);
        return c -> op.applyAsDouble(x.eval(c), y.eval(c));
    }

    private void arity(String f, List<Expr> a, int n) {
        if (a.size() != n) {
            throw error("math." + f + " takes " + n + " argument(s), got " + a.size());
        }
    }

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw error("nested too deeply");
        }
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private boolean peek(char c) {
        return i < s.length() && s.charAt(i) == c;
    }

    private boolean eat(String token) {
        if (s.startsWith(token, i)) {
            i += token.length();
            return true;
        }
        return false;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("Molang '" + s + "' at " + i + ": " + what);
    }
}
