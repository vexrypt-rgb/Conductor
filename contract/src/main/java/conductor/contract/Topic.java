package conductor.contract;

import java.util.Objects;

/**
 * A typed publish/subscribe channel between modules. Two topics are the same when name and type are equal.
 * For version-agnostic modules, {@code type} should be a JDK, contract, or shared plain-Java API type.
 */
public record Topic<T>(String name, Class<T> type) {

    public Topic {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
