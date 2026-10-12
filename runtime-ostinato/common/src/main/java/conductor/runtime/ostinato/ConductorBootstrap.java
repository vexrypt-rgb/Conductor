package conductor.runtime.ostinato;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import conductor.contract.Conductor;
import conductor.contract.ConductorModule;
import conductor.contract.DiagnosticSink;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.ReleasePolicy;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Starts a {@link ConductorModule} on Ostinato. The Conductor mod calls this for modules it finds; modules
 * themselves never need to.
 * <p>
 * A background thread waits until the player is in a world. All further work happens on the game thread: install
 * Conductor on the primary Baritone, call {@link ConductorModule#onReady}, then {@link ConductorModule#onTick} every
 * in-world tick (after Conductor's own tick). The background thread never touches game state itself; it only hands
 * work to the game thread through {@code Minecraft.submit}.
 */
public final class ConductorBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger("conductor");

    private ConductorBootstrap() {}

    /** Start {@code module} with its own release policy and a fresh in-memory diagnostic sink. */
    public static void start(ConductorModule module) {
        start(module, module.releasePolicy());
    }

    /** Start {@code module} with a fresh in-memory diagnostic sink. */
    public static void start(ConductorModule module, ReleasePolicy policy) {
        start(module, policy, new InMemoryDiagnosticSink(10_000));
    }

    public static void start(ConductorModule module, ReleasePolicy policy, DiagnosticSink sink) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(sink, "sink");
        Thread waiter = new Thread(() -> waitThenInstall(module, policy, sink), "conductor-bootstrap");
        waiter.setDaemon(true);
        waiter.start();
    }

    private static void waitThenInstall(ConductorModule module, ReleasePolicy policy, DiagnosticSink sink) {
        try {
            while (true) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.submit(() -> mc.player != null && mc.level != null).get(30, TimeUnit.SECONDS)) {
                    mc.submit(() -> install(module, policy, sink)).get(30, TimeUnit.SECONDS);
                    return;
                }
                Thread.sleep(250);
            }
        } catch (Exception e) {
            LOG.error("Conductor bootstrap failed; module {} not started", module.getClass().getName(), e);
        }
    }

    /** Game thread. */
    private static void install(ConductorModule module, ReleasePolicy policy, DiagnosticSink sink) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        Conductor conductor = ConductorOstinato.install(baritone, sink, policy);
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onTick(TickEvent event) {
                if (event.getType() == TickEvent.Type.IN) {
                    call(module, () -> module.onTick(conductor));
                }
            }
        });
        call(module, () -> module.onReady(conductor));
    }

    /** A failing module must not take the game tick down with it. */
    private static void call(ConductorModule module, Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException e) {
            LOG.error("Conductor module {} threw", module.getClass().getName(), e);
        }
    }
}
