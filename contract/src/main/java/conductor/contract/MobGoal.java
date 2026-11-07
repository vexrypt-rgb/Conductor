package conductor.contract;

/**
 * One thing a custom mob's AI does, built from vanilla's own goals. A mob's goals are listed in priority order: the
 * first that can run wins. {@link #targetPlayers()} and {@link #retaliate()} choose whom to attack; the others decide
 * what to do. Make your own on top of them with a {@link MobBehaviour}.
 *
 * @param speed      walking speed while doing it, relative to the mob's speed attribute (1 = normal)
 * @param range      blocks: how far it looks, shoots or searches
 * @param interval   ticks between ranged attacks
 * @param projectile what {@link Kind#RANGED_ATTACK} shoots
 */
public final class MobGoal {
    private final Kind kind;
    private final double speed;
    private final double range;
    private final int interval;
    private final MobProjectile projectile;

    public MobGoal(Kind kind, double speed, double range, int interval, MobProjectile projectile) {
        this.kind = kind;
        this.speed = speed;
        this.range = range;
        this.interval = interval;
        this.projectile = projectile;
    }

    public Kind kind() { return kind; }

    public double speed() { return speed; }

    public double range() { return range; }

    public int interval() { return interval; }

    public MobProjectile projectile() { return projectile; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MobGoal)) return false;
        MobGoal other = (MobGoal) o;
        return java.util.Arrays.deepEquals(new Object[] {this.kind, this.speed, this.range, this.interval, this.projectile}, new Object[] {other.kind, other.speed, other.range, other.interval, other.projectile});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.kind, this.speed, this.range, this.interval, this.projectile}); }

    @Override public String toString() { return "MobGoal[" + "kind=" + kind + ", " + "speed=" + speed + ", " + "range=" + range + ", " + "interval=" + interval + ", " + "projectile=" + projectile + "]"; }


    public enum Kind { FLOAT, MELEE_ATTACK, RANGED_ATTACK, WANDER, LOOK_AT_PLAYERS, LOOK_AROUND, PANIC, TARGET_PLAYERS, RETALIATE }

    /** Swim up in water and lava instead of sinking. */
    public static MobGoal swim() {
        return new MobGoal(Kind.FLOAT, 1, 0, 0, null);
    }

    /** Walk to the target and hit it (damage: the mob's attack damage). */
    public static MobGoal meleeAttack(double speed) {
        return new MobGoal(Kind.MELEE_ATTACK, speed, 0, 0, null);
    }

    /** Keep within {@code range} blocks of the target and shoot every {@code interval} ticks. */
    public static MobGoal rangedAttack(MobProjectile projectile, int interval, double range) {
        return new MobGoal(Kind.RANGED_ATTACK, 1, range, interval, projectile);
    }

    /** Walk about at random, avoiding water. */
    public static MobGoal wander(double speed) {
        return new MobGoal(Kind.WANDER, speed, 0, 0, null);
    }

    /** Look at players within {@code range} blocks. */
    public static MobGoal lookAtPlayers(double range) {
        return new MobGoal(Kind.LOOK_AT_PLAYERS, 1, range, 0, null);
    }

    /** Look around at random. */
    public static MobGoal lookAround() {
        return new MobGoal(Kind.LOOK_AROUND, 1, 0, 0, null);
    }

    /** Run about when hurt. */
    public static MobGoal panic(double speed) {
        return new MobGoal(Kind.PANIC, speed, 0, 0, null);
    }

    /** Target the nearest player it can see (survival and adventure players only, as vanilla does). */
    public static MobGoal targetPlayers() {
        return new MobGoal(Kind.TARGET_PLAYERS, 1, 0, 0, null);
    }

    /** Target whoever hurt it. */
    public static MobGoal retaliate() {
        return new MobGoal(Kind.RETALIATE, 1, 0, 0, null);
    }

    /** Whether this goal picks a target rather than an action. */
    public boolean targeting() {
        return kind == Kind.TARGET_PLAYERS || kind == Kind.RETALIATE;
    }
}
