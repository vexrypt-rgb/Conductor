package conductor.contract;

import java.util.OptionalDouble;

/**
 * One entity near the player, read in one tick. Ids are valid only within the current context and may be reused
 * by the game later; use them right away.
 *
 * @param id      the game's entity id, for {@link Conductor#attack(int)}
 * @param type    entity type id, e.g. {@code minecraft:zombie}
 * @param health  for living entities
 * @param hostile the game classes it as an enemy (monsters, including those not yet angry)
 */
public final class EntityInfo {
    private final int id;
    private final String type;
    private final String name;
    private final double x;
    private final double y;
    private final double z;
    private final double distance;
    private final OptionalDouble health;
    private final boolean player;
    private final boolean hostile;

    public EntityInfo(int id, String type, String name, double x, double y, double z, double distance, OptionalDouble health, boolean player, boolean hostile) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.distance = distance;
        this.health = health;
        this.player = player;
        this.hostile = hostile;
    }

    public int id() { return id; }

    public String type() { return type; }

    public String name() { return name; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public double distance() { return distance; }

    public OptionalDouble health() { return health; }

    public boolean player() { return player; }

    public boolean hostile() { return hostile; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityInfo)) return false;
        EntityInfo other = (EntityInfo) o;
        return java.util.Arrays.deepEquals(new Object[] {this.id, this.type, this.name, this.x, this.y, this.z, this.distance, this.health, this.player, this.hostile}, new Object[] {other.id, other.type, other.name, other.x, other.y, other.z, other.distance, other.health, other.player, other.hostile});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.id, this.type, this.name, this.x, this.y, this.z, this.distance, this.health, this.player, this.hostile}); }

    @Override public String toString() { return "EntityInfo[" + "id=" + id + ", " + "type=" + type + ", " + "name=" + name + ", " + "x=" + x + ", " + "y=" + y + ", " + "z=" + z + ", " + "distance=" + distance + ", " + "health=" + health + ", " + "player=" + player + ", " + "hostile=" + hostile + "]"; }
}
