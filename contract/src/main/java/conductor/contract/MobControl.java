package conductor.contract;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One custom mob, as its {@link MobBehaviour} sees it during a call. Positions are in blocks. Actions return whether
 * the game accepted them (false: e.g. no target, an unknown id). Radii are capped at 64 blocks.
 */
public interface MobControl {

    /** The mob's type id, e.g. {@code ruby:ruby_golem}. */
    String type();

    /** The game's entity id. */
    int entityId();

    double x();

    double y();

    double z();

    double health();

    double maxHealth();

    default double healthFraction() {
        return maxHealth() <= 0 ? 0 : health() / maxHealth();
    }

    /** Ticks since this mob was spawned or loaded on this server. */
    long age();

    boolean onGround();

    /** What it is attacking; {@link EntityInfo#distance()} is measured from the mob. */
    Optional<EntityInfo> target();

    /** Players within {@code radius} blocks, nearest first (spectators left out). */
    List<EntityInfo> playersWithin(double radius);

    /** This mob's own state for the behaviour, kept while the mob is loaded (not saved with the world). */
    Map<String, Object> memory();

    /** Play an animation from the mob's animation file on every client that sees it. */
    boolean animate(String animation);

    /** Shoot at the target. */
    boolean shoot(MobProjectile projectile);

    /** Jump toward the target: {@code horizontal} and {@code vertical} are speeds in blocks per tick. */
    boolean leap(double horizontal, double vertical);

    /**
     * Hurt living things within {@code radius}; returns how many were hit. Never itself or mobs of its own type, and a
     * hostile mob never hits other monsters (its summoned minions are safe from its own slam).
     */
    int damageNearby(double radius, double amount);

    /** Push living things within {@code radius} away from the mob (as {@link #damageNearby} picks them); returns how many. */
    int pushNearby(double radius, double strength);

    /** Give an effect, e.g. {@code minecraft:slowness}, to living things within {@code radius} (as {@link #damageNearby} picks them). */
    int effectNearby(double radius, String effect, int seconds, int amplifier);

    /** Spawn an entity, e.g. {@code ruby:ruby_zombie}, at an offset from the mob. */
    boolean summon(String entityType, double dx, double dy, double dz);

    boolean teleport(double x, double y, double z);

    void heal(double amount);

    /** Play a sound, e.g. {@code minecraft:entity.iron_golem.attack}, at the mob. */
    void sound(String sound, float volume, float pitch);

    /** Show a particle with no options, e.g. {@code minecraft:explosion} or {@code minecraft:flame}, around the mob. */
    void particles(String particle, int count, double spread);

    /** A chat message to players within {@code radius}. */
    void say(String message, double radius);

    /** Change the boss bar's colour (no effect if the mob has no boss bar). */
    void bossBar(BossBar.Color color);

    /** A line in the game's log, prefixed with the mob type. */
    void log(String message);
}
