package conductor.contract;

/** A block position, as plain integers, so modules never need Minecraft's own position classes. */
public final class BlockPoint {
    private final int x;
    private final int y;
    private final int z;

    public BlockPoint(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() { return x; }

    public int y() { return y; }

    public int z() { return z; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockPoint)) return false;
        BlockPoint other = (BlockPoint) o;
        return java.util.Arrays.deepEquals(new Object[] {this.x, this.y, this.z}, new Object[] {other.x, other.y, other.z});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.x, this.y, this.z}); }

    @Override public String toString() { return "BlockPoint[" + "x=" + x + ", " + "y=" + y + ", " + "z=" + z + "]"; }


    public BlockPoint offset(int dx, int dy, int dz) {
        return new BlockPoint(x + dx, y + dy, z + dz);
    }
}
