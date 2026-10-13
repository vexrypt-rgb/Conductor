package conductor.contract;

import java.util.Objects;

/**
 * What a combat operation fights. Plain data: the runtime maps it onto the engine's own combat processes.
 * <p>
 * The goal says <em>whom</em> to fight, never <em>how</em>. Which weapons and tricks the engine uses depends on the
 * engine build and on what exists in the game; ask with {@link Conductor#content(ContentId)}.
 */
public final class CombatGoal {
    private final Kind kind;
    private final String name;

    public CombatGoal(Kind kind, String name) {
        Objects.requireNonNull(kind, "kind");
        boolean named = kind == Kind.PLAYER || kind == Kind.MOB;
        if (named && (name == null || name.trim().isEmpty())) {
        throw new IllegalArgumentException(kind + " needs a name");
        }
        if (!named && name != null) {
        throw new IllegalArgumentException(kind + " takes no name");
        }

        this.kind = kind;
        this.name = name;
    }

    public Kind kind() { return kind; }

    public String name() { return name; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CombatGoal)) return false;
        CombatGoal other = (CombatGoal) o;
        return java.util.Arrays.deepEquals(new Object[] {this.kind, this.name}, new Object[] {other.kind, other.name});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.kind, this.name}); }

    @Override public String toString() { return "CombatGoal[" + "kind=" + kind + ", " + "name=" + name + "]"; }


    public enum Kind {
        /** One player, by name (case-insensitive). */
        PLAYER,
        /** Every other player in reach. */
        PLAYERS,
        /** Hostile mobs. */
        HOSTILES,
        /** One mob type, by entity id, e.g. {@code minecraft:zombie}. */
        MOB
    }

    public static CombatGoal player(String name) {
        return new CombatGoal(Kind.PLAYER, name);
    }

    public static CombatGoal players() {
        return new CombatGoal(Kind.PLAYERS, null);
    }

    public static CombatGoal hostiles() {
        return new CombatGoal(Kind.HOSTILES, null);
    }

    public static CombatGoal mob(String entityId) {
        return new CombatGoal(Kind.MOB, entityId);
    }
}
