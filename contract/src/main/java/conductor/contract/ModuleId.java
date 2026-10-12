package conductor.contract;

import java.util.Objects;

/** Identity of one module, assigned by the host (from the providing mod's id). Modules cannot choose their own. */
public record ModuleId(String value) {

    /** The engine core itself, for calls made outside any module (tests, the single-consumer API). */
    public static final ModuleId CORE = new ModuleId("conductor:core");

    public ModuleId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("blank module id");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
