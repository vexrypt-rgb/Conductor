package conductor.harness;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.movement.IMovementEngine;
import baritone.api.movement.MovementGoal;
import baritone.api.movement.PathResult;
import baritone.movement.MovementBackends;
import conductor.contract.Conductor;
import conductor.contract.DiagnosticEvent;
import conductor.contract.DiscoveryResult;
import conductor.contract.FreshnessPolicy;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.NavigationGoal;
import conductor.contract.Observation;
import conductor.contract.ObservationKinds;
import conductor.contract.ObservationResult;
import conductor.contract.OperationId;
import conductor.contract.OperationResult;
import conductor.runtime.ostinato.ConductorOstinato;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Scenario controller. Runs on its own thread and does all game work through {@code Minecraft.submit}, so every
 * Conductor and engine call happens on the game thread. The deliberate exception is the off-thread probe.
 */
final class Scenario implements Runnable {

    private static final int[] CANCEL_AFTER_TICKS = {0, 1, 5, 20, 40, 60};
    private static final int OBSERVE_AFTER_CANCEL = 200;

    private final JsonLog log;
    private final Minecraft mc = Minecraft.getInstance();
    private TickRecorder recorder;

    Scenario(JsonLog log) {
        this.log = log;
    }

    @Override
    public void run() {
        try {
            scenario();
            event("done", "ok", true);
        } catch (Throwable t) {
            event("done", "ok", false, "error", String.valueOf(t));
            t.printStackTrace();
        } finally {
            try {
                mc.submit(mc::stop);
            } catch (Throwable ignored) {
            }
        }
    }

    private void scenario() throws Exception {
        String server = System.getProperty("conductor.harness.server");
        if (server != null) {
            event("waiting for title screen");
            long titleDeadline = System.currentTimeMillis() + 300_000;
            // First launch can show an onboarding screen instead of the title screen; any screen will do once
            // resource loading (the overlay) has finished.
            String lastScreen = "";
            while (!onGame(() -> mc.screen != null && mc.getOverlay() == null)) {
                String screen = onGame(() -> mc.screen == null ? "none" : mc.screen.getClass().getName());
                if (!screen.equals(lastScreen)) {
                    event("screen", "class", screen);
                    lastScreen = screen;
                }
                if (System.currentTimeMillis() > titleDeadline) {
                    throw new IllegalStateException("no screen after loading; last " + lastScreen);
                }
                Thread.sleep(500);
            }
            event("screen ready", "class", onGame(() -> mc.screen.getClass().getName()),
                    "isTitle", onGame(() -> mc.screen instanceof TitleScreen));
            event("connecting", "server", server);
            onGame(() -> {
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(server),
                        new ServerData("conductor-harness", server, ServerData.Type.OTHER), false, null);
                return null;
            });
        }
        event("waiting for world");
        long deadline = System.currentTimeMillis() + 300_000;
        while (!onGame(() -> mc.player != null && mc.level != null)) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("never entered a world");
            }
            Thread.sleep(500);
        }
        Thread.sleep(5000); // let chunks arrive

        InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(10_000);
        IBaritone baritone = onGame(() -> BaritoneAPI.getProvider().getPrimaryBaritone());
        Conductor conductor = onGame(() -> ConductorOstinato.install(baritone, sink));
        recorder = new TickRecorder(conductor, log);
        onGame(() -> {
            baritone.getGameEventHandler().registerEventListener(recorder);
            return null;
        });
        waitTicks(5);

        // ---- Stage 2, live ----
        recorder.phase = "S2";
        DiscoveryResult discovery = onGame(conductor::discover);
        event("S2 discover", "accepted", discovery.isAccepted(), "context", String.valueOf(discovery.context()),
                "capabilities", String.valueOf(discovery.capabilities()));
        ObservationResult<Boolean> offThread = conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick());
        event("S2 off-thread observe", "rejection", String.valueOf(offThread.rejection()));
        ObservationResult<Boolean> noPolicy = onGame(() -> conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, null));
        event("S2 observe without policy", "rejection", String.valueOf(noPolicy.rejection()));
        FreshnessPolicy fiveTicks = FreshnessPolicy.maxAge(OptionalLong.of(5), Optional.empty());
        Observation<Boolean> held = onGame(() -> conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, fiveTicks)).observation().orElseThrow();
        waitTicks(10);
        ObservationResult<Boolean> stale = onGame(() -> conductor.revalidate(held, fiveTicks));
        event("S2 revalidate after 10 ticks with max 5", "rejection", String.valueOf(stale.rejection()),
                "freshness", String.valueOf(stale.freshness()), "availability", String.valueOf(held.availability()));
        Observation<Boolean> beforeDimensionChange = onGame(() ->
                conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, FreshnessPolicy.maxAge(OptionalLong.of(100_000), Optional.empty())))
                .observation().orElseThrow();

        // ---- Stage 3, live: one contract operation ----
        recorder.phase = "S3";
        BlockPos start = onGame(() -> mc.player.blockPosition());
        OperationResult travel = onGame(() -> conductor.requestTravel(NavigationGoal.getToBlock(start.getX() + 24, start.getY(), start.getZ())));
        event("S3 requestTravel", "result", String.valueOf(travel));
        OperationId op = travel.status().map(s -> s.id()).orElse(null);
        waitTicks(30);
        if (op != null) {
            event("S3 cancel", "result", String.valueOf(onGame(() -> conductor.cancel(op))));
        }
        recorder.phase = "S3 after cancel";
        waitTicks(OBSERVE_AFTER_CANCEL);
        event("S3 status after observation window", "result", String.valueOf(op == null ? null : onGame(() -> conductor.operationStatus(op))));
        event("S3 second requestTravel", "result",
                String.valueOf(onGame(() -> conductor.requestTravel(NavigationGoal.getToBlock(start.getX(), start.getY(), start.getZ())))));

        // ---- Stage 4 evidence: direct engine trials (outside the contract) ----
        IMovementEngine engine = onGame(() -> MovementBackends.engine(baritone));
        int direction = -1;
        for (int k : CANCEL_AFTER_TICKS) {
            recorder.phase = "E wait-idle k=" + k;
            boolean idle = waitIdle(20, 600);
            BlockPos from = onGame(() -> mc.player.blockPosition());
            BlockPos target = from.offset(40 * direction, 0, 0);
            direction = -direction;
            recorder.phase = "E k=" + k + " moving";
            PathResult result = onGame(() -> {
                PathResult r = engine.goTo(MovementGoal.getToBlock(target));
                if (k == 0) {
                    engine.cancel();
                }
                return r;
            });
            long goTick = recorder.ticks;
            event("E goTo", "k", k, "idleBefore", idle, "from", String.valueOf(from), "target", String.valueOf(target), "result", String.valueOf(result));
            if (k > 0) {
                waitTicks(k);
                onGame(() -> {
                    engine.cancel();
                    return null;
                });
            }
            event("E cancel", "k", k, "ticksAfterGoTo", recorder.ticks - goTick);
            recorder.phase = "E k=" + k + " after cancel";
            waitTicks(OBSERVE_AFTER_CANCEL);
        }

        // ---- Dimension change: context and authority invalidation, live ----
        recorder.phase = "D";
        event("D teleport to nether roof");
        onGame(() -> {
            mc.player.connection.sendCommand("execute in minecraft:the_nether run tp @s 0.5 128 0.5");
            return null;
        });
        long dimDeadline = System.currentTimeMillis() + 30_000;
        while (!onGame(() -> mc.level.dimension().identifier().toString()).equals("minecraft:the_nether")) {
            if (System.currentTimeMillis() > dimDeadline) {
                event("D dimension never changed (is the player op?)");
                break;
            }
            Thread.sleep(200);
        }
        waitTicks(40);
        event("D revalidate pre-change observation", "result",
                String.valueOf(onGame(() -> conductor.revalidate(beforeDimensionChange, FreshnessPolicy.maxAge(OptionalLong.of(100_000), Optional.empty())))));
        event("D operation status", "result", String.valueOf(op == null ? null : onGame(() -> conductor.operationStatus(op))));
        event("D requestTravel after context change", "result",
                String.valueOf(onGame(() -> conductor.requestTravel(NavigationGoal.xz(5, 5)))));
        event("D discover", "context", String.valueOf(onGame(conductor::discover).context()));

        for (DiagnosticEvent e : sink.snapshot()) {
            log.write(JsonLog.obj("type", "sink", "event", e.type().name(), "thread", e.thread(), "tick", e.tick(),
                    "context", String.valueOf(e.context()), "subject", e.subject(), "detail", e.detail()));
        }
    }

    private boolean waitIdle(int consecutive, int maxTicks) throws InterruptedException {
        long start = recorder.ticks;
        long idleSince = -1;
        while (recorder.ticks - start < maxTicks) {
            long now = recorder.ticks;
            if (recorder.looksIdle()) {
                if (idleSince < 0) {
                    idleSince = now;
                }
                if (now - idleSince >= consecutive) {
                    return true;
                }
            } else {
                idleSince = -1;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private void waitTicks(long n) throws InterruptedException {
        long target = recorder.ticks + n;
        while (recorder.ticks < target) {
            Thread.sleep(10);
        }
    }

    private <T> T onGame(Supplier<T> work) throws Exception {
        return mc.submit(work).get(60, TimeUnit.SECONDS);
    }

    private void event(String name, Object... kv) {
        Object[] all = new Object[kv.length + 6];
        all[0] = "type";
        all[1] = "event";
        all[2] = "name";
        all[3] = name;
        all[4] = "tick";
        all[5] = recorder == null ? -1 : recorder.ticks;
        System.arraycopy(kv, 0, all, 6, kv.length);
        log.write(JsonLog.obj(all));
    }
}
