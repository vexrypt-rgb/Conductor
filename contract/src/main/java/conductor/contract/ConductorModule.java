package conductor.contract;

/**
 * A feature built on Conductor.
 * <p>
 * A module uses only this package, never Minecraft or the bot engine, so the same module jar works with every
 * Minecraft version Conductor has a runtime for. Declare it in your {@code fabric.mod.json} under the
 * {@code "conductor"} entrypoint; the Conductor mod for the running Minecraft version finds and starts it.
 * <p>
 * All methods are called on the game thread, so the module can call every {@link Conductor} method directly. Keep
 * them short: they run inside the game tick.
 */
public interface ConductorModule {

    /**
     * How long the bot must look idle before Conductor calls it free ({@link ReleasePolicy}). There is deliberately
     * no default; every module chooses.
     */
    ReleasePolicy releasePolicy();

    /** Called once, after Conductor is installed and the player is in a world. */
    void onReady(Conductor conductor);

    /** Called every in-world game tick, after Conductor has updated the context and the release evidence. */
    default void onTick(Conductor conductor) {
    }
}
