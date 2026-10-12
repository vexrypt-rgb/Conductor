package conductor.contract;

import java.util.Set;

/**
 * A feature built on Conductor. Many modules can run side by side; they coordinate through leases, topics and
 * services (see docs/MODULE-PLATFORM.md).
 * <p>
 * A <em>contract module</em> uses only this package and the JDK, so the same jar works with every Minecraft version
 * Conductor has a runtime for. A <em>native module</em> may also use Minecraft or the engine directly (and is then
 * version-specific); it should still take leases before touching shared resources.
 * <p>
 * Declare a module in your {@code fabric.mod.json} under the {@code "conductor"} entrypoint. All methods are called
 * on the game thread; keep them short, they run inside the game tick.
 */
public interface ConductorModule {

    /**
     * How long the bot must look idle before Conductor calls it free ({@link ReleasePolicy}). There is deliberately
     * no default. With several modules, Conductor uses the longest window any module asks for.
     */
    ReleasePolicy releasePolicy();

    /** Called once when the module starts (in a world, with its required services available). */
    void onReady(ModuleContext conductor);

    /** Called every in-world game tick while the module runs. */
    default void onTick(ModuleContext conductor) {
    }

    /** Default priority for this module's leases. */
    default Priority priority() {
        return Priority.NORMAL;
    }

    /**
     * Optional parts of this module that need content which may not exist everywhere (see {@link ModuleFeature}).
     * Conductor checks them when the module starts; ask {@link ModuleContext#feature(String)} which are on.
     */
    default java.util.List<ModuleFeature> features() {
        return java.util.List.of();
    }

    /** Services that must be provided before this module starts. */
    default Set<ServiceKey<?>> requires() {
        return Set.of();
    }

    /** A PENDING request was granted. */
    default void onLeaseGranted(ModuleContext conductor, Lease lease) {
    }

    /** A higher-priority module took {@code lease}: stop using the resource now. */
    default void onLeaseRevoked(ModuleContext conductor, Lease lease, ModuleId by) {
    }

    /** The module is being stopped (disabled, or a required service went away). Its leases are revoked after this. */
    default void onDisable(ModuleContext conductor) {
    }
}
