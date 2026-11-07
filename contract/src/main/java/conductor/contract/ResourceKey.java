package conductor.contract;

import java.util.Objects;

/**
 * A shared thing that at most one module may hold at a time.
 *
 * @param evidenceBacked whether Conductor can observe that the thing is no longer in use (only MOVEMENT today). For
 *                       other resources, hand-over relies on the holder acknowledging revocation.
 */
public final class ResourceKey {
    private final String name;
    private final boolean evidenceBacked;

    public ResourceKey(String name, boolean evidenceBacked) {
        Objects.requireNonNull(name, "name");

        this.name = name;
        this.evidenceBacked = evidenceBacked;
    }

    public String name() { return name; }

    public boolean evidenceBacked() { return evidenceBacked; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceKey)) return false;
        ResourceKey other = (ResourceKey) o;
        return java.util.Arrays.deepEquals(new Object[] {this.name, this.evidenceBacked}, new Object[] {other.name, other.evidenceBacked});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.evidenceBacked}); }



    @Override
    public String toString() {
        return name;
    }
}
