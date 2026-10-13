package conductor.contract;

/**
 * A box inside a block, in pixels (0 to 16 on each axis), the same numbers as a block model's {@code from} and
 * {@code to}. For a {@link BlockShape#MODEL} block it is given as seen when the block faces north.
 */
public record BlockBox(double x1, double y1, double z1, double x2, double y2, double z2) {

    boolean valid() {
        return in(x1) && in(y1) && in(z1) && in(x2) && in(y2) && in(z2) && x1 < x2 && y1 < y2 && z1 < z2;
    }

    private static boolean in(double v) {
        return v >= 0 && v <= 16;
    }

    /** This box turned with a block that faces {@code facing} (north, east, south or west) instead of north. */
    public BlockBox facing(String facing) {
        return switch (facing) {
            case "north" -> this;
            case "east" -> new BlockBox(16 - z2, y1, x1, 16 - z1, y2, x2);
            case "south" -> new BlockBox(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case "west" -> new BlockBox(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> throw new IllegalArgumentException("not a horizontal direction: " + facing);
        };
    }
}
