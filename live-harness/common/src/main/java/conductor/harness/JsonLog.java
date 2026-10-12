package conductor.harness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Append-only JSON-lines log, safe from any thread. */
final class JsonLog {

    private final Writer out;

    JsonLog(String path) {
        try {
            out = Files.newBufferedWriter(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    synchronized void write(Map<String, Object> line) {
        try {
            out.write(json(line));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String json(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (sb.length() > 1) {
                    sb.append(',');
                }
                sb.append(json(String.valueOf(e.getKey()))).append(':').append(json(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (v instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            for (Object o : it) {
                if (sb.length() > 1) {
                    sb.append(',');
                }
                sb.append(json(o));
            }
            return sb.append(']').toString();
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : v.toString().toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c < 0x20 ? ' ' : c);
            }
        }
        return sb.append('"').toString();
    }
}
