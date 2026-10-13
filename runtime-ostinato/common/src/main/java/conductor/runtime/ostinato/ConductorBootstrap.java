package conductor.runtime.ostinato;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import conductor.contract.ContractService;
import conductor.contract.ConductorModule;
import conductor.contract.DiagnosticSink;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.ModuleHost;
import conductor.contract.ReleasePolicy;
import conductor.contract.SettingsStore;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Starts {@link ConductorModule}s on Ostinato, side by side under one {@link ModuleHost}. The Conductor mod calls
 * this for the modules it finds; modules themselves never need to.
 * <p>
 * A background thread waits until the player is in a world. All further work happens on the game thread: install
 * Conductor on the primary Baritone, call {@link ConductorModule#onReady}, then {@link ConductorModule#onTick} every
 * in-world tick (after Conductor's own tick). The background thread never touches game state itself; it only hands
 * work to the game thread through {@code Minecraft.submit}.
 */
public final class ConductorBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger("conductor");

    private ConductorBootstrap() {}

    /** Start one module, with a fresh in-memory diagnostic sink. */
    public static void start(ConductorModule module) {
        start(new LinkedHashMap<>(Map.of(module.getClass().getName(), module)), new InMemoryDiagnosticSink(10_000));
    }

    /**
     * Start every module in {@code modules} (requested id → module) side by side on one Conductor core. The core
     * uses the longest release windows any module asks for ({@link ModuleHost#combinedPolicy}).
     */
    public static void start(Map<String, ConductorModule> modules, DiagnosticSink sink) {
        start(modules, sink, SettingsStore.IN_MEMORY);
    }

    /** As {@link #start(Map, DiagnosticSink)}, keeping module settings in {@code settings}. */
    public static void start(Map<String, ConductorModule> modules, DiagnosticSink sink, SettingsStore settings) {
        Objects.requireNonNull(modules, "modules");
        Objects.requireNonNull(sink, "sink");
        Map<String, ConductorModule> copy = new LinkedHashMap<>(modules);
        Thread waiter = new Thread(() -> waitThenInstall(copy, sink, settings), "conductor-bootstrap");
        waiter.setDaemon(true);
        waiter.start();
    }

    private static void waitThenInstall(Map<String, ConductorModule> modules, DiagnosticSink sink, SettingsStore settings) {
        try {
            while (true) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.submit(() -> mc.player != null && mc.level != null).get(30, TimeUnit.SECONDS)) {
                    mc.submit(() -> install(modules, sink, settings)).get(30, TimeUnit.SECONDS);
                    return;
                }
                Thread.sleep(250);
            }
        } catch (Exception e) {
            LOG.error("Conductor bootstrap failed; modules {} not started", modules.keySet(), e);
        }
    }

    /** Game thread. */
    private static void install(Map<String, ConductorModule> modules, DiagnosticSink sink, SettingsStore settings) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        ReleasePolicy policy = ModuleHost.combinedPolicy(modules.values());
        ContractService core = ConductorOstinato.install(baritone, sink, policy);
        ModuleHost host = new ModuleHost(core, System.getProperty("conductor.commandPrefix", ModuleHost.DEFAULT_COMMAND_PREFIX));
        host.useSettingsStore(settings);
        modules.forEach((id, module) -> LOG.info("Conductor registered module {}", host.register(id, module)));
        LOG.info("Conductor core installed; release policy {}", policy);
        // Registered after Conductor's own listener, so each tick the context and evidence are already updated.
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onTick(TickEvent event) {
                if (event.getType() != TickEvent.Type.IN) {
                    return;
                }
                try {
                    host.tick();
                } catch (RuntimeException e) {
                    // the host isolates module faults itself; this guards against a platform bug taking the game down
                    LOG.error("Conductor module host failed this tick", e);
                }
            }
        });
    }

}
