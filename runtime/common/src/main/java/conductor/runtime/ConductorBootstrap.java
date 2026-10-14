package conductor.runtime;

import conductor.contract.ConductorModule;
import conductor.contract.ContractService;
import conductor.contract.DiagnosticSink;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.ModuleHost;
import conductor.contract.ReleasePolicy;
import conductor.contract.SettingsStore;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Starts {@link ConductorModule}s side by side under one {@link ModuleHost}. The host mod calls this for the modules
 * it finds; modules never need to.
 * <p>
 * Everything happens on the game thread, driven by Conductor's own client-tick hook ({@link Hooks#clientTick}): on
 * the first tick in a world Conductor installs its runtime (attaching a movement engine if one is installed),
 * registers the modules, and from then on runs the contract tick and the module host every in-world tick.
 */
public final class ConductorBootstrap {

    private static final Logger LOG = LogManager.getLogger("conductor");

    private ConductorBootstrap() {}

    /** Start one module, with a fresh in-memory diagnostic sink. */
    public static void start(ConductorModule module) {
        start(new LinkedHashMap<>(Jdk8.mapOf(module.getClass().getName(), module)), new InMemoryDiagnosticSink(10_000));
    }

    public static void start(Map<String, ConductorModule> modules, DiagnosticSink sink) {
        start(modules, sink, SettingsStore.IN_MEMORY);
    }

    /**
     * Start every module in {@code modules} (requested id → module) on one Conductor core, keeping module settings in
     * {@code settings}. The core uses the longest release windows any module asks for ({@link ModuleHost#combinedPolicy}).
     */
    public static void start(Map<String, ConductorModule> modules, DiagnosticSink sink, SettingsStore settings) {
        Objects.requireNonNull(modules, "modules");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(settings, "settings");
        Map<String, ConductorModule> copy = new LinkedHashMap<>(modules);
        GameAccess game = new MinecraftGameAccess();
        Hooks.onClientTick(() -> {
            if (game.levelIdentity() != null && game.hasPlayer()) {
                install(copy, sink, settings);
            }
        });
    }

    /** Game thread, first tick in a world. */
    private static void install(Map<String, ConductorModule> modules, DiagnosticSink sink, SettingsStore settings) {
        ReleasePolicy policy = ModuleHost.combinedPolicy(modules.values());
        ConductorRuntime.Installed installed = ConductorRuntime.install(sink, policy);
        ContractService core = installed.service();
        ModuleHost host = new ModuleHost(core, System.getProperty("conductor.commandPrefix", ModuleHost.DEFAULT_COMMAND_PREFIX));
        host.useSettingsStore(settings);
        modules.forEach((id, module) -> LOG.info("Conductor registered module {}", host.register(id, module)));
        installed.afterEachInWorldTick(() -> {
            try {
                host.tick();
            } catch (RuntimeException e) {
                // the host isolates module faults itself; this guards against a platform bug taking the game down
                LOG.error("Conductor module host failed this tick", e);
            }
        });
        Hooks.onClientTick(installed.tick());
        LOG.info("Conductor core installed (engine: {}); release policy {}", installed.engine(), policy);
        installed.tick().run(); // this tick counts
    }
}
