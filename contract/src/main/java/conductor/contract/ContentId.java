package conductor.contract;

import java.util.Objects;

/**
 * Something a module may need that differs between Minecraft versions or engine builds: a game item, block, entity
 * or effect (by its namespaced registry id), or an engine feature.
 * <p>
 * Modules ask about the thing itself, never about a version number. "Does {@code minecraft:iron_spear} exist?"
 * works on any version, including ones released after the module was written.
 */
public record ContentId(Kind kind, String id) {

    public enum Kind {
        /** A game item, e.g. {@code minecraft:iron_spear}. */
        ITEM,
        /** A game block, e.g. {@code minecraft:crafter}. */
        BLOCK,
        /** An entity type, e.g. {@code minecraft:breeze}. */
        ENTITY,
        /** A status effect, e.g. {@code minecraft:wind_charged}. */
        EFFECT,
        /** A feature of the engine build, e.g. {@code ostinato:combat/spear}. Listed by the runtime in discovery. */
        ENGINE
    }

    public ContentId {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static ContentId item(String id) {
        return new ContentId(Kind.ITEM, id);
    }

    public static ContentId block(String id) {
        return new ContentId(Kind.BLOCK, id);
    }

    public static ContentId entity(String id) {
        return new ContentId(Kind.ENTITY, id);
    }

    public static ContentId effect(String id) {
        return new ContentId(Kind.EFFECT, id);
    }

    public static ContentId engine(String id) {
        return new ContentId(Kind.ENGINE, id);
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + ":" + id;
    }
}
