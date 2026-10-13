package conductor.contract;

/**
 * The vanilla mob a declared mob is built on: its AI, sounds, body, hitbox and renderer. The declared mob changes
 * its name, texture, size, health, speed, attack damage and drops (loot table {@code entities/<path>} in the module's
 * data). {@link #CUSTOM} builds on none of them.
 */
public enum MobArchetype {
    /** Hostile, walks, attacks in melee, burns in daylight. */
    ZOMBIE,
    /** Hostile at night, climbs walls. */
    SPIDER,
    /** Passive, wanders, follows carrots. Babies from breeding are vanilla pigs. */
    PIG,
    /**
     * Conductor's own mob, set by {@link MobDeclaration.Builder#model}: the module's model, animations, AI goals,
     * behaviour and boss bar, with no vanilla mob underneath.
     */
    CUSTOM
}
