package conductor.contract;

/** What a custom mob shoots, with {@link MobGoal#rangedAttack} or {@link MobControl#shoot}. Vanilla's own projectiles. */
public enum MobProjectile {
    /** A skeleton's arrow: damage grows with speed; the player can't pick it up. */
    ARROW,
    /** A blaze's fireball: sets what it hits on fire. */
    SMALL_FIREBALL,
    /** No damage (except to blazes); knocks back. */
    SNOWBALL
}
