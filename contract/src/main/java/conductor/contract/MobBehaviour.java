package conductor.contract;

/**
 * A custom mob's own code, on top of its {@link MobGoal goals}: boss phases, special attacks, summons. One instance
 * serves every mob of the type; keep per-mob state in {@link MobControl#memory()}.
 * <p>
 * Runs on the server (or the single-player world's server), on the game thread, once per tick per mob. It must
 * return quickly and must not keep the {@link MobControl} after the call. If a method throws, Conductor logs it and
 * stops calling this behaviour for that mob type; the mobs keep their goals and the game keeps running.
 */
public interface MobBehaviour {

    /** The first tick this mob exists on this server (after spawning, or after its chunk was loaded again). */
    default void spawned(MobControl mob) {
    }

    /** Every tick while the mob is alive. */
    default void tick(MobControl mob) {
    }

    /** The tick after it lost health; {@code amount} is the health lost. */
    default void hurt(MobControl mob, double amount) {
    }

    /** Once, when it dies. */
    default void died(MobControl mob) {
    }
}
