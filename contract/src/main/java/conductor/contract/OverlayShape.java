package conductor.contract;

/**
 * Something drawn in the 3D world for the player to see: an outlined box, a line, or a tracer (a line from the
 * crosshair to a point). World coordinates in blocks; colour as {@code 0xAARRGGBB}.
 *
 * @param throughWalls drawn even when blocks are in front of it
 */
public record OverlayShape(Kind kind, double x1, double y1, double z1, double x2, double y2, double z2, int argb,
                           boolean throughWalls) {

    public enum Kind {
        /** Outline of the box from (x1, y1, z1) to (x2, y2, z2). */
        BOX,
        /** A line from (x1, y1, z1) to (x2, y2, z2). */
        LINE,
        /** A line from the crosshair to (x1, y1, z1); the other coordinates are ignored. */
        TRACER
    }

    public OverlayShape {
        if (kind == null) {
            throw new IllegalArgumentException("kind is null");
        }
        for (double v : new double[]{x1, y1, z1, x2, y2, z2}) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("coordinates must be finite");
            }
        }
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
