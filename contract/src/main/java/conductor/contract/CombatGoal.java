package conductor.contract;

import java.util.Objects;

/**
 * What a combat operation fights. Plain data: the runtime maps it onto the engine's own combat processes.
 * <p>
 * The goal says <em>whom</em> to fight, never <em>how</em>. Which weapons and tricks the engine uses depends on the
 * engine build and on what exists in the game; ask with {@link Conductor#content(ContentId)}.
 */
public record CombatGoal(Kind kind, String name) {

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

    public CombatGoal {
        Objects.requireNonNull(kind, "kind");
        boolean named = kind == Kind.PLAYER || kind == Kind.MOB;
        if (named && (name == null || name.isBlank())) {
            throw new IllegalArgumentException(kind + " needs a name");
        }
        if (!named && name != null) {
            throw new IllegalArgumentException(kind + " takes no name");
        }
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
