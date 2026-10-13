package conductor.runtime;

import conductor.contract.ContextTracker;
import conductor.contract.ContractService;
import conductor.contract.DiagnosticSink;
import conductor.contract.ReleasePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Installs Conductor's runtime: Minecraft reads, Conductor's own hooks, and an optional movement engine found through
 * {@link EngineLayerProvider} services. Needs no engine and no loader API.
 */
public final class ConductorRuntime {

    private static final Logger LOG = LoggerFactory.getLogger("conductor");

    /** The installed core: the contract service plus the pieces the bootstrap and tests drive. */
    public record Installed(ContractService service, String engine, Runnable tick, Lifecycle lifecycle) {

        /** Run {@code hook} on every in-world tick, after the contract's own tick. */
        public void afterEachInWorldTick(Runnable hook) {
            lifecycle.onInWorldTick(() -> {
                service.tick();
                hook.run();
            });
        }
    }

    private ConductorRuntime() {}

    /** Game thread, in a world. Engines are looked up in this class's loader. */
    public static Installed install(DiagnosticSink sink, ReleasePolicy policy) {
        GameAccess game = new MinecraftGameAccess();
        if (!game.isGameThread()) {
            throw new IllegalStateException("ConductorRuntime.install must be called on the game thread");
        }
        EngineLayer engine = findEngine(ConductorRuntime.class.getClassLoader());
        OverlayRenderer renderer = null;
        if (Hooks.lineDrawer() != null) {
            OverlayRenderer r = new OverlayRenderer(Hooks.lineDrawer());
            Hooks.renderer(r::render);
            renderer = r;
        }
        HookEventFeed feed = new HookEventFeed(game);
        Hooks.events(feed);
        return install(sink, policy, game, new MinecraftPlayerAccess(game), feed, renderer, engine,
                EngineFeatures.ofLoader(ConductorRuntime.class.getClassLoader()));
    }

    static Installed install(DiagnosticSink sink, ReleasePolicy policy, GameAccess game, PlayerAccess player,
                             HookEventFeed feed, conductor.contract.OverlayPort overlays, EngineLayer engine,
                             EngineFeatures features) {
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(policy, "policy");
        ContextTracker contexts = new ContextTracker(sink);
        Lifecycle lifecycle = new Lifecycle(contexts, game);
        CoreRuntime runtime = new CoreRuntime(game, lifecycle, features);
        if (player != null) {
            runtime.enablePlayer(player);
        }
        if (feed != null) {
            runtime.enableEvents(feed);
        }
        if (overlays != null) {
            runtime.enableOverlays(overlays);
        }
        if (engine != null) {
            runtime.attachEngine(engine);
        }
        ContractService service = new ContractService(runtime, contexts, sink, policy);
        lifecycle.onInWorldTick(service::tick);
        return new Installed(service, engine == null ? "none" : engine.name(), lifecycle::tick, lifecycle);
    }

    /** The first available engine layer, or null: Conductor then runs without a movement engine. */
    static EngineLayer findEngine(ClassLoader loader) {
        for (ServiceLoader.Provider<EngineLayerProvider> p : ServiceLoader.load(EngineLayerProvider.class, loader).stream().toList()) {
            try {
                EngineLayerProvider provider = p.get();
                if (provider.available(loader)) {
                    EngineLayer layer = provider.create();
                    LOG.info("Conductor engine: {}", layer.name());
                    return layer;
                }
            } catch (RuntimeException | LinkageError e) {
                LOG.warn("Conductor engine provider {} failed; continuing without it: {}", p.type().getName(), e.toString());
            }
        }
        LOG.info("Conductor engine: none (no movement engine installed; travel and combat are unavailable)");
        return null;
    }
}
