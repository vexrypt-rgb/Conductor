package conductor.contract;

/**
 * A player on the server, read in one tick.
 *
 * @param name      the in-game name
 * @param uuid      the account's id, as text; stays the same across deaths and rejoins
 * @param dimension e.g. {@code minecraft:overworld}
 * @param health    0 while dead
 * @param spectator in spectator mode
 */
public final class OnlinePlayer {
    private final String name;
    private final String uuid;
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final double health;
    private final boolean spectator;

    public OnlinePlayer(String name, String uuid, String dimension, double x, double y, double z, double health, boolean spectator) {
        this.name = name;
        this.uuid = uuid;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.health = health;
        this.spectator = spectator;
    }

    public String name() { return name; }

    public String uuid() { return uuid; }

    public String dimension() { return dimension; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public double health() { return health; }

    public boolean spectator() { return spectator; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OnlinePlayer)) return false;
        OnlinePlayer other = (OnlinePlayer) o;
        return java.util.Arrays.deepEquals(new Object[] {this.name, this.uuid, this.dimension, this.x, this.y, this.z, this.health, this.spectator}, new Object[] {other.name, other.uuid, other.dimension, other.x, other.y, other.z, other.health, other.spectator});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.uuid, this.dimension, this.x, this.y, this.z, this.health, this.spectator}); }

    @Override public String toString() { return "OnlinePlayer[" + "name=" + name + ", " + "uuid=" + uuid + ", " + "dimension=" + dimension + ", " + "x=" + x + ", " + "y=" + y + ", " + "z=" + z + ", " + "health=" + health + ", " + "spectator=" + spectator + "]"; }


    public boolean alive() {
        return health > 0;
    }
}
