package conductor.contract;

/**
 * The local player's condition, read in one tick.
 *
 * @param gameMode {@code survival}, {@code creative}, {@code adventure} or {@code spectator}
 */
public final class PlayerStatus {
    private final double health;
    private final double absorption;
    private final double maxHealth;
    private final int food;
    private final double saturation;
    private final int air;
    private final boolean onGround;
    private final boolean sprinting;
    private final boolean sneaking;
    private final boolean inWater;
    private final boolean fallFlying;
    private final String gameMode;

    public PlayerStatus(double health, double absorption, double maxHealth, int food, double saturation, int air, boolean onGround, boolean sprinting, boolean sneaking, boolean inWater, boolean fallFlying, String gameMode) {
        this.health = health;
        this.absorption = absorption;
        this.maxHealth = maxHealth;
        this.food = food;
        this.saturation = saturation;
        this.air = air;
        this.onGround = onGround;
        this.sprinting = sprinting;
        this.sneaking = sneaking;
        this.inWater = inWater;
        this.fallFlying = fallFlying;
        this.gameMode = gameMode;
    }

    public double health() { return health; }

    public double absorption() { return absorption; }

    public double maxHealth() { return maxHealth; }

    public int food() { return food; }

    public double saturation() { return saturation; }

    public int air() { return air; }

    public boolean onGround() { return onGround; }

    public boolean sprinting() { return sprinting; }

    public boolean sneaking() { return sneaking; }

    public boolean inWater() { return inWater; }

    public boolean fallFlying() { return fallFlying; }

    public String gameMode() { return gameMode; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerStatus)) return false;
        PlayerStatus other = (PlayerStatus) o;
        return java.util.Arrays.deepEquals(new Object[] {this.health, this.absorption, this.maxHealth, this.food, this.saturation, this.air, this.onGround, this.sprinting, this.sneaking, this.inWater, this.fallFlying, this.gameMode}, new Object[] {other.health, other.absorption, other.maxHealth, other.food, other.saturation, other.air, other.onGround, other.sprinting, other.sneaking, other.inWater, other.fallFlying, other.gameMode});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.health, this.absorption, this.maxHealth, this.food, this.saturation, this.air, this.onGround, this.sprinting, this.sneaking, this.inWater, this.fallFlying, this.gameMode}); }

    @Override public String toString() { return "PlayerStatus[" + "health=" + health + ", " + "absorption=" + absorption + ", " + "maxHealth=" + maxHealth + ", " + "food=" + food + ", " + "saturation=" + saturation + ", " + "air=" + air + ", " + "onGround=" + onGround + ", " + "sprinting=" + sprinting + ", " + "sneaking=" + sneaking + ", " + "inWater=" + inWater + ", " + "fallFlying=" + fallFlying + ", " + "gameMode=" + gameMode + "]"; }
}
