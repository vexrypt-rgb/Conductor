package conductor.contract;

import java.util.Objects;

/** A typed key for a service one module provides to others. Usually an interface type. */
public record ServiceKey<T>(String name, Class<T> type) {

    public ServiceKey {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
