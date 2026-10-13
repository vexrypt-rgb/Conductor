package conductor.contract;

import java.util.Objects;

/**
 * Engine-neutral travel target. The runtime integration maps it to its engine's goal type.
 *
 * @param range used by {@link Kind#NEAR} only; at least 1
 */
public record NavigationGoal(Kind kind, int x, int y, int z, int range) {

    public enum Kind {
        /** Stand at the block. */
        BLOCK,
        /** Stand next to or on the block. */
        GET_TO_BLOCK,
        /** Within {@code range} of the block. */
        NEAR,
        /** Reach the column; {@code y} is ignored. */
        XZ
    }

    public NavigationGoal {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.NEAR && range < 1) {
            throw new IllegalArgumentException("NEAR needs range >= 1: " + range);
        }
        if (kind != Kind.NEAR && range != 0) {
            throw new IllegalArgumentException(kind + " takes no range");
        }
        if (kind == Kind.XZ && y != 0) {
            throw new IllegalArgumentException("XZ takes no y");
        }
    }

    public static NavigationGoal block(int x, int y, int z) {
        return new NavigationGoal(Kind.BLOCK, x, y, z, 0);
    }

    public static NavigationGoal getToBlock(int x, int y, int z) {
        return new NavigationGoal(Kind.GET_TO_BLOCK, x, y, z, 0);
    }

    public static NavigationGoal near(int x, int y, int z, int range) {
        return new NavigationGoal(Kind.NEAR, x, y, z, range);
    }

    public static NavigationGoal xz(int x, int z) {
        return new NavigationGoal(Kind.XZ, x, 0, z, 0);
    }
}
