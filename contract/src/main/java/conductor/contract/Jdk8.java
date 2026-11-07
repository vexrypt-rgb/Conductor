package conductor.contract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java 8 stand-ins for the immutable-collection factories added in Java 9 and 10.
 * The contract is compiled at release 8 so the legacy runtimes (1.8.9, beta 1.7.3) can load it.
 */
final class Jdk8 {
    private Jdk8() {
    }

    @SafeVarargs
    static <T> List<T> listOf(T... items) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(items)));
    }

    static <T> List<T> copyList(java.util.Collection<? extends T> items) {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    @SafeVarargs
    static <T> Set<T> setOf(T... items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(items)));
    }

    static <T> Set<T> copySet(java.util.Collection<? extends T> items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(items));
    }

    static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    static <K, V> Map<K, V> copyMap(Map<? extends K, ? extends V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
