package conductor.contract;

/**
 * The local player's condition, read in one tick.
 *
 * @param gameMode {@code survival}, {@code creative}, {@code adventure} or {@code spectator}
 */
public record PlayerStatus(double health, double absorption, double maxHealth, int food, double saturation, int air,
                           boolean onGround, boolean sprinting, boolean sneaking, boolean inWater, boolean fallFlying,
                           String gameMode) {
}
