package conductor.contract;

import java.util.Objects;

/**
 * A typed observation key. Kinds are compared by identity; the contract defines them in {@link ObservationKinds}.
 *
 * @param <T> value type carried when the observation is {@link Availability#OBSERVED}
 */
public final class ObservationKind<T> {

    private final String name;
    private final String definition;

    ObservationKind(String name, String definition) {
        this.name = Objects.requireNonNull(name, "name");
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    public String name() {
        return name;
    }

    /** Normative meaning of an {@link Availability#OBSERVED} value of this kind. */
    public String definition() {
        return definition;
    }

    @Override
    public String toString() {
        return name;
    }
}
