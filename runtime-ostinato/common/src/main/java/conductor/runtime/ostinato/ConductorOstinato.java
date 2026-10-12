package conductor.runtime.ostinato;

import baritone.api.IBaritone;
import baritone.api.movement.IMovementEngine;
import baritone.movement.MovementBackends;
import conductor.contract.Conductor;
import conductor.contract.ContextTracker;
import conductor.contract.ContractService;
import conductor.contract.DiagnosticSink;
import conductor.contract.ReleasePolicy;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Entry point: connects the contract to one Ostinato {@link IBaritone} for one consumer. */
public final class ConductorOstinato {

    private static final Set<IBaritone> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());

    private ConductorOstinato() {}

    /**
     * Register a lifecycle listener on {@code baritone} and return the binding for one consumer. Call on the game
     * thread. {@code policy} sets the release-evidence windows; it has no defaults (see {@link ReleasePolicy}). A second install on the same {@code IBaritone} is refused: Stage 3 is a single-consumer experiment,
     * not a shared service.
     * <p>
     * Changes made to Ostinato: one event listener that reads tick and world events, and one
     * {@code HybridMovementEngine} instance, held for this consumer. There is no current context until the next
     * in-world tick.
     */
    public static Conductor install(IBaritone baritone, DiagnosticSink sink, ReleasePolicy policy) {
        Objects.requireNonNull(baritone, "baritone");
        GameAccess game = new MinecraftGameAccess(baritone.getPlayerContext());
        if (!game.isGameThread()) {
            throw new IllegalStateException("ConductorOstinato.install must be called on the game thread");
        }
        return install(baritone, sink, policy, game, new TungstenBackendAccess(), MovementBackends.engine(baritone),
                MovementBackends::preferTungstenTravel);
    }

    /** Without a release policy: discovery and observation work, but navigation is never granted. */
    public static Conductor install(IBaritone baritone, DiagnosticSink sink) {
        return install(baritone, sink, ReleasePolicy.none());
    }

    static Conductor install(IBaritone baritone, DiagnosticSink sink, ReleasePolicy policy, GameAccess game,
                             SecondaryBackendAccess secondary, IMovementEngine engine, BooleanSupplier prefersSecondaryTravel) {
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(policy, "policy");
        synchronized (INSTALLED) {
            if (!INSTALLED.add(baritone)) {
                throw new IllegalStateException("Conductor is already installed for this IBaritone (single consumer only)");
            }
        }
        ContextTracker contexts = new ContextTracker(sink);
        LifecycleListener lifecycle = new LifecycleListener(contexts, game, sink);
        OstinatoRuntimeIntegration runtime = new OstinatoRuntimeIntegration(game, lifecycle,
                new OstinatoSignals(baritone, secondary), secondary,
                new OstinatoNavigationPort(engine, prefersSecondaryTravel));
        ContractService service = new ContractService(runtime, contexts, sink, policy);
        lifecycle.onInWorldTick(service::tick);
        baritone.getGameEventHandler().registerEventListener(lifecycle);
        return service;
    }
}
