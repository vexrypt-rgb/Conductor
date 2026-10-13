package conductor.contract;

/**
 * A box inside a block, in pixels (0 to 16 on each axis), the same numbers as a block model's {@code from} and
 * {@code to}. For a {@link BlockShape#MODEL} block it is given as seen when the block faces north.
 */
public final class BlockBox {
    private final double x1;
    private final double y1;
    private final double z1;
    private final double x2;
    private final double y2;
    private final double z2;

    public BlockBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
    }

    public double x1() { return x1; }

    public double y1() { return y1; }

    public double z1() { return z1; }

    public double x2() { return x2; }

    public double y2() { return y2; }

    public double z2() { return z2; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockBox)) return false;
        BlockBox other = (BlockBox) o;
        return java.util.Arrays.deepEquals(new Object[] {this.x1, this.y1, this.z1, this.x2, this.y2, this.z2}, new Object[] {other.x1, other.y1, other.z1, other.x2, other.y2, other.z2});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.x1, this.y1, this.z1, this.x2, this.y2, this.z2}); }

    @Override public String toString() { return "BlockBox[" + "x1=" + x1 + ", " + "y1=" + y1 + ", " + "z1=" + z1 + ", " + "x2=" + x2 + ", " + "y2=" + y2 + ", " + "z2=" + z2 + "]"; }


    boolean valid() {
        return in(x1) && in(y1) && in(z1) && in(x2) && in(y2) && in(z2) && x1 < x2 && y1 < y2 && z1 < z2;
    }

    private static boolean in(double v) {
        return v >= 0 && v <= 16;
    }

    /** This box turned with a block that faces {@code facing} (north, east, south or west) instead of north. */
    public BlockBox facing(String facing) {
        switch (facing) {
            case "north": return this;
            case "east": return new BlockBox(16 - z2, y1, x1, 16 - z1, y2, x2);
            case "south": return new BlockBox(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case "west": return new BlockBox(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default: throw new IllegalArgumentException("not a horizontal direction: " + facing);
        }
    }
}
