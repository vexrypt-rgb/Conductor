package conductor.contract;

import java.util.List;
import java.util.Objects;

/**
 * An optional part of a module that needs certain content, e.g. spear tactics need spear items and the engine's spear
 * combat. Conductor turns it on only when <em>all</em> of {@code requires} are {@link ContentResult.Presence#PRESENT};
 * otherwise the rest of the module runs without it.
 */
public record ModuleFeature(String name, String description, List<ContentId> requires) {

    public ModuleFeature {
        Objects.requireNonNull(name, "name");
        description = description == null ? "" : description;
        requires = List.copyOf(requires);
    }

    public static ModuleFeature of(String name, String description, ContentId... requires) {
        return new ModuleFeature(name, description, List.of(requires));
    }
}
