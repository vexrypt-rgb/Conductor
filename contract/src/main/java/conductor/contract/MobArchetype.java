package conductor.contract;

/**
 * The vanilla mob a declared mob is built on: its AI, sounds, body, hitbox and renderer. The declared mob changes
 * its name, texture, size, health, speed, attack damage and drops (loot table {@code entities/<path>} in the module's
 * data).
 */
public enum MobArchetype {
    /** Hostile, walks, attacks in melee, burns in daylight. */
    ZOMBIE,
    /** Hostile at night, climbs walls. */
    SPIDER,
    /** Passive, wanders, follows carrots. Babies from breeding are vanilla pigs. */
    PIG
}
