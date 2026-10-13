package conductor.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader for module files that every Minecraft version reads the same way (mob models and animations).
 * Objects become {@link LinkedHashMap}s, arrays {@link List}s, numbers {@link Double}s. It does not depend on the
 * game's bundled Gson, whose API changed between the versions Conductor runs on.
 */
public final class MiniJson {

    private static final int MAX_DEPTH = 64;

    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    /** Parse one JSON value. Throws {@link IllegalArgumentException} naming the position of the first error. */
    public static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        p.space();
        Object v = p.value(0);
        p.space();
        if (p.i != text.length()) {
            throw p.error("text after the end of the JSON value");
        }
        return v;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("nested too deeply");
        }
        if (i >= s.length()) {
            throw error("unexpected end");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{': return object(depth);
            case '[': return array(depth);
            case '"': return string();
            case 't': return word("true", Boolean.TRUE);
            case 'f': return word("false", Boolean.FALSE);
            case 'n': return word("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("unexpected '" + c + "'");
        }
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        i++;
        space();
        if (peek('}')) {
            i++;
            return map;
        }
        while (true) {
            space();
            if (!peek('"')) {
                throw error("expected a key");
            }
            String key = string();
            space();
            expect(':');
            space();
            map.put(key, value(depth + 1));
            space();
            if (peek(',')) {
                i++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        i++;
        space();
        if (peek(']')) {
            i++;
            return list;
        }
        while (true) {
            space();
            list.add(value(depth + 1));
            space();
            if (peek(',')) {
                i++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String string() {
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw error("unterminated string");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return b.toString();
            }
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (i >= s.length()) {
                throw error("unterminated string");
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"', '\\', '/' -> b.append(e);
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) {
                        throw error("bad \\u escape");
                    }
                    try {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("bad \\u escape");
                    }
                    i += 4;
                }
                default -> throw error("bad escape \\" + e);
            }
        }
    }

    private Double number() {
        int start = i;
        if (peek('-')) {
            i++;
        }
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        try {
            return Double.valueOf(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw error("bad number '" + s.substring(start, i) + "'");
        }
    }

    private Object word(String w, Object v) {
        if (!s.startsWith(w, i)) {
            throw error("unexpected '" + s.charAt(i) + "'");
        }
        i += w.length();
        return v;
    }

    private void space() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') {
                i++;
            } else {
                return;
            }
        }
    }

    private boolean peek(char c) {
        return i < s.length() && s.charAt(i) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("expected '" + c + "'");
        }
        i++;
    }

    private IllegalArgumentException error(String what) {
        int line = 1;
        int col = 1;
        for (int k = 0; k < Math.min(i, s.length()); k++) {
            if (s.charAt(k) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new IllegalArgumentException("JSON line " + line + " column " + col + ": " + what);
    }
}
