package conductor.contract;

/**
 * Something drawn in the 3D world for the player to see: an outlined box, a line, or a tracer (a line from the
 * crosshair to a point). World coordinates in blocks; colour as {@code 0xAARRGGBB}.
 *
 * @param throughWalls drawn even when blocks are in front of it
 */
public final class OverlayShape {
    private final Kind kind;
    private final double x1;
    private final double y1;
    private final double z1;
    private final double x2;
    private final double y2;
    private final double z2;
    private final int argb;
    private final boolean throughWalls;

    public OverlayShape(Kind kind, double x1, double y1, double z1, double x2, double y2, double z2, int argb, boolean throughWalls) {
        if (kind == null) {
        throw new IllegalArgumentException("kind is null");
        }
        for (double v : new double[]{x1, y1, z1, x2, y2, z2}) {
        if (!Double.isFinite(v)) {
        throw new IllegalArgumentException("coordinates must be finite");
        }
        }

        this.kind = kind;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.argb = argb;
        this.throughWalls = throughWalls;
    }

    public Kind kind() { return kind; }

    public double x1() { return x1; }

    public double y1() { return y1; }

    public double z1() { return z1; }

    public double x2() { return x2; }

    public double y2() { return y2; }

    public double z2() { return z2; }

    public int argb() { return argb; }

    public boolean throughWalls() { return throughWalls; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OverlayShape)) return false;
        OverlayShape other = (OverlayShape) o;
        return java.util.Arrays.deepEquals(new Object[] {this.kind, this.x1, this.y1, this.z1, this.x2, this.y2, this.z2, this.argb, this.throughWalls}, new Object[] {other.kind, other.x1, other.y1, other.z1, other.x2, other.y2, other.z2, other.argb, other.throughWalls});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.kind, this.x1, this.y1, this.z1, this.x2, this.y2, this.z2, this.argb, this.throughWalls}); }

    @Override public String toString() { return "OverlayShape[" + "kind=" + kind + ", " + "x1=" + x1 + ", " + "y1=" + y1 + ", " + "z1=" + z1 + ", " + "x2=" + x2 + ", " + "y2=" + y2 + ", " + "z2=" + z2 + ", " + "argb=" + argb + ", " + "throughWalls=" + throughWalls + "]"; }


    public enum Kind {
        /** Outline of the box from (x1, y1, z1) to (x2, y2, z2). */
        BOX,
        /** A line from (x1, y1, z1) to (x2, y2, z2). */
        LINE,
        /** A line from the crosshair to (x1, y1, z1); the other coordinates are ignored. */
        TRACER
    }

    /** Outline of one block. */
    public static OverlayShape block(BlockPoint at, int argb) {
        return new OverlayShape(Kind.BOX, at.x(), at.y(), at.z(), at.x() + 1, at.y() + 1, at.z() + 1, argb, false);
    }

    public static OverlayShape box(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        return new OverlayShape(Kind.BOX, x1, y1, z1, x2, y2, z2, argb, false);
    }

    public static OverlayShape line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        return new OverlayShape(Kind.LINE, x1, y1, z1, x2, y2, z2, argb, false);
    }

    /** Tracers are drawn through walls by default; that is what they are for. */
    public static OverlayShape tracer(double x, double y, double z, int argb) {
        return new OverlayShape(Kind.TRACER, x, y, z, x, y, z, argb, true);
    }

    public OverlayShape throughWalls(boolean through) {
        return new OverlayShape(kind, x1, y1, z1, x2, y2, z2, argb, through);
    }
}
