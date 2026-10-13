package conductor.contract;

/**
 * Where the local player is and how it moves, read in one tick. Position in blocks; velocity in blocks per tick;
 * yaw and pitch in degrees, as the game reports them (pitch -90 looks straight up, 90 straight down).
 */
public record PlayerMotion(double x, double y, double z, double vx, double vy, double vz, double yaw, double pitch) {
}
