package conductor.runtime.ostinato;

import baritone.api.IBaritone;
import conductor.contract.Conductor;
import conductor.contract.ContextTracker;
import conductor.contract.ContractService;
import conductor.contract.DiagnosticSink;

import java.util.Objects;

/** Entry point: connects the contract to one Ostinato {@link IBaritone}. */
public final class ConductorOstinato {

    private ConductorOstinato() {}

    /**
     * Register a lifecycle listener on {@code baritone} and return the Stage 2 binding. Call on the game thread.
     * <p>
     * The only change made to Ostinato is adding one event listener that reads tick and world events. There is no
     * current context until the next in-world tick, so observations made before then are rejected.
     */
    public static Conductor install(IBaritone baritone, DiagnosticSink sink) {
        Objects.requireNonNull(baritone, "baritone");
        Objects.requireNonNull(sink, "sink");
        GameAccess game = new MinecraftGameAccess(baritone.getPlayerContext());
        if (!game.isGameThread()) {
            throw new IllegalStateException("ConductorOstinato.install must be called on the game thread");
        }
        ContextTracker contexts = new ContextTracker(sink);
        LifecycleListener lifecycle = new LifecycleListener(contexts, game, sink);
        SecondaryBackendAccess secondary = new TungstenBackendAccess();
        OstinatoRuntimeIntegration runtime = new OstinatoRuntimeIntegration(game, lifecycle,
                new OstinatoSignals(baritone, secondary), secondary);
        baritone.getGameEventHandler().registerEventListener(lifecycle);
        return new ContractService(runtime, contexts, sink);
    }
}
