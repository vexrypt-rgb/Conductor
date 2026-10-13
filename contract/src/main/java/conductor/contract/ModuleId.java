package conductor.contract;

import java.util.Objects;

/** Identity of one module, assigned by the host (from the providing mod's id). Modules cannot choose their own. */
public final class ModuleId {
    private final String value;

    public ModuleId(String value) {
        Objects.requireNonNull(value, "value");
        if (value.trim().isEmpty()) {
        throw new IllegalArgumentException("blank module id");
        }

        this.value = value;
    }

    public String value() { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleId)) return false;
        ModuleId other = (ModuleId) o;
        return java.util.Arrays.deepEquals(new Object[] {this.value}, new Object[] {other.value});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.value}); }



    /** The engine core itself, for calls made outside any module (tests, the single-consumer API). */
    public static final ModuleId CORE = new ModuleId("conductor:core");

    @Override
    public String toString() {
        return value;
    }
}
