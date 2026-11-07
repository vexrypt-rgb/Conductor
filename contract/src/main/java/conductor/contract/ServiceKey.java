package conductor.contract;

import java.util.Objects;

/** A typed key for a service one module provides to others. Usually an interface type. */
public final class ServiceKey<T> {
    private final String name;
    private final Class<T> type;

    public ServiceKey(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");

        this.name = name;
        this.type = type;
    }

    public String name() { return name; }

    public Class<T> type() { return type; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceKey)) return false;
        ServiceKey<?> other = (ServiceKey<?>) o;
        return java.util.Arrays.deepEquals(new Object[] {this.name, this.type}, new Object[] {other.name, other.type});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.type}); }

    @Override public String toString() { return "ServiceKey[" + "name=" + name + ", " + "type=" + type + "]"; }
}
