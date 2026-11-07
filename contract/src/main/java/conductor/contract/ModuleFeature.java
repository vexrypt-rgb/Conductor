package conductor.contract;

import java.util.List;
import java.util.Objects;

/**
 * An optional part of a module that needs certain content, e.g. spear tactics need spear items and the engine's spear
 * combat. Conductor turns it on only when <em>all</em> of {@code requires} are {@link ContentResult.Presence#PRESENT};
 * otherwise the rest of the module runs without it.
 */
public final class ModuleFeature {
    private final String name;
    private final String description;
    private final List<ContentId> requires;

    public ModuleFeature(String name, String description, List<ContentId> requires) {
        Objects.requireNonNull(name, "name");
        description = description == null ? "" : description;
        requires = Jdk8.copyList(requires);

        this.name = name;
        this.description = description;
        this.requires = requires;
    }

    public String name() { return name; }

    public String description() { return description; }

    public List<ContentId> requires() { return requires; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleFeature)) return false;
        ModuleFeature other = (ModuleFeature) o;
        return java.util.Arrays.deepEquals(new Object[] {this.name, this.description, this.requires}, new Object[] {other.name, other.description, other.requires});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.description, this.requires}); }

    @Override public String toString() { return "ModuleFeature[" + "name=" + name + ", " + "description=" + description + ", " + "requires=" + requires + "]"; }


    public static ModuleFeature of(String name, String description, ContentId... requires) {
        return new ModuleFeature(name, description, Jdk8.listOf(requires));
    }
}
