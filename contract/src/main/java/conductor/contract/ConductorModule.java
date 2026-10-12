package conductor.contract;

/**
 * A feature built on Conductor. Implement this, then start it from your mod's entrypoint with the runtime's
 * bootstrap (for Ostinato: {@code ConductorBootstrap.start(module, releasePolicy)}).
 * <p>
 * Both methods are called on the game thread, so the module can call every {@link Conductor} method directly. Keep
 * them short: they run inside the game tick.
 */
public interface ConductorModule {

    /** Called once, after Conductor is installed and the player is in a world. */
    void onReady(Conductor conductor);

    /** Called every in-world game tick, after Conductor has updated the context and the release evidence. */
    default void onTick(Conductor conductor) {
    }
}
