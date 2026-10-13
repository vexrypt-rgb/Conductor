package conductor.contract;

import java.util.Objects;

/**
 * Something a module may need that differs between Minecraft versions or engine builds: a game item, block, entity
 * or effect (by its namespaced registry id), or an engine feature.
 * <p>
 * Modules ask about the thing itself, never about a version number. "Does {@code minecraft:iron_spear} exist?"
 * works on any version, including ones released after the module was written.
 */
public final class ContentId {
    private final Kind kind;
    private final String id;

    public ContentId(Kind kind, String id) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");

        this.kind = kind;
        this.id = id;
    }

    public Kind kind() { return kind; }

    public String id() { return id; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentId)) return false;
        ContentId other = (ContentId) o;
        return java.util.Arrays.deepEquals(new Object[] {this.kind, this.id}, new Object[] {other.kind, other.id});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.kind, this.id}); }



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
