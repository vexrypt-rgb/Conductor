package conductor.contract;

/**
 * Where the local player is and how it moves, read in one tick. Position in blocks; velocity in blocks per tick;
 * yaw and pitch in degrees, as the game reports them (pitch -90 looks straight up, 90 straight down).
 */
public final class PlayerMotion {
    private final double x;
    private final double y;
    private final double z;
    private final double vx;
    private final double vy;
    private final double vz;
    private final double yaw;
    private final double pitch;

    public PlayerMotion(double x, double y, double z, double vx, double vy, double vz, double yaw, double pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public double vx() { return vx; }

    public double vy() { return vy; }

    public double vz() { return vz; }

    public double yaw() { return yaw; }

    public double pitch() { return pitch; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerMotion)) return false;
        PlayerMotion other = (PlayerMotion) o;
        return java.util.Arrays.deepEquals(new Object[] {this.x, this.y, this.z, this.vx, this.vy, this.vz, this.yaw, this.pitch}, new Object[] {other.x, other.y, other.z, other.vx, other.vy, other.vz, other.yaw, other.pitch});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.x, this.y, this.z, this.vx, this.vy, this.vz, this.yaw, this.pitch}); }

    @Override public String toString() { return "PlayerMotion[" + "x=" + x + ", " + "y=" + y + ", " + "z=" + z + ", " + "vx=" + vx + ", " + "vy=" + vy + ", " + "vz=" + vz + ", " + "yaw=" + yaw + ", " + "pitch=" + pitch + "]"; }
}
