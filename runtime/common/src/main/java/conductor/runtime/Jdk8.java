package conductor.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java 8 stand-ins for the immutable-collection factories added in Java 9 and 10, and for String.repeat (Java 11).
 * The runtimes are compiled at release 8 so the legacy versions (1.8.9, beta 1.7.3) can load them.
 */
final class Jdk8 {
    private Jdk8() {
    }

    @SafeVarargs
    static <T> List<T> listOf(T... items) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(items)));
    }

    static <T> List<T> copyList(Collection<? extends T> items) {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    @SafeVarargs
    static <T> Set<T> setOf(T... items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(items)));
    }

    static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    static <K, V> Map<K, V> mapOf(K k1, V v1) {
        Map<K, V> m = new LinkedHashMap<>();
        m.put(k1, v1);
        return Collections.unmodifiableMap(m);
    }

    static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        Map<K, V> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return Collections.unmodifiableMap(m);
    }

    static <K, V> Map<K, V> copyMap(Map<? extends K, ? extends V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) {
            b.append(s);
        }
        return b.toString();
    }
}
