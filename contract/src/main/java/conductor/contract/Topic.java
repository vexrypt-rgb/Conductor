package conductor.contract;

import java.util.Objects;

/**
 * A typed publish/subscribe channel between modules. Two topics are the same when name and type are equal.
 * For version-agnostic modules, {@code type} should be a JDK, contract, or shared plain-Java API type.
 */
public final class Topic<T> {
    private final String name;
    private final Class<T> type;

    public Topic(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");

        this.name = name;
        this.type = type;
    }

    public String name() { return name; }

    public Class<T> type() { return type; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Topic)) return false;
        Topic<?> other = (Topic<?>) o;
        return java.util.Arrays.deepEquals(new Object[] {this.name, this.type}, new Object[] {other.name, other.type});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.type}); }

    @Override public String toString() { return "Topic[" + "name=" + name + ", " + "type=" + type + "]"; }
}
