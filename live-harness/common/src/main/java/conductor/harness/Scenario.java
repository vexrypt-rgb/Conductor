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
import conductor.contract.OperationState;
import conductor.contract.ReleasePolicy;
import conductor.contract.ResourceState;
import conductor.runtime.ostinato.ConductorOstinato;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
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
    final Minecraft mc = Minecraft.getInstance();
    TickRecorder recorder;

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
                Compat.connect(mc, server);
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
        // Release windows chosen from the evidence runs (docs/live-run-2026-10-12.md): control signals clear before
        // movement ends, and momentum lasts at most 11 ticks. They are harness settings, not contract defaults.
        ReleasePolicy policy = new ReleasePolicy(OptionalLong.of(20), OptionalLong.of(1200));
        Conductor conductor = onGame(() -> ConductorOstinato.install(baritone, sink, policy));
        recorder = new TickRecorder(conductor, log);
        onGame(() -> {
            baritone.getGameEventHandler().registerEventListener(recorder);
            return null;
        });
        waitTicks(5);

        if ("evidence".equals(System.getProperty("conductor.harness.scenario"))) {
            new EvidenceTrials(this, baritone).run();
            dumpSink(sink);
            return;
        }

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
        // ---- Stage 3 + 4, live ----
        recorder.phase = "S4 wait available";
        event("S4 resource available", "ok", waitResource(ResourceState.AVAILABLE, 400));

        // 1. cancel mid-path: CANCELLING until evidence, then CANCELLED
        recorder.phase = "S4 travel+cancel";
        BlockPos start = onGame(() -> mc.player.blockPosition());
        OperationResult travel = onGame(() -> conductor.requestTravel(NavigationGoal.getToBlock(start.getX() + 40, start.getY(), start.getZ())));
        event("S4 requestTravel", "result", String.valueOf(travel));
        OperationId op = travel.status().map(st -> st.id()).orElse(null);
        recorder.operation = op;
        waitTicks(20);
        event("S4 cancel", "result", String.valueOf(onGame(() -> conductor.cancel(op))));
        long cancelTick = recorder.ticks;
        boolean cancelled = waitOperation(op, OperationState.CANCELLED, 1000);
        event("S4 confirmed", "cancelled", cancelled, "ticksAfterCancel", recorder.ticks - cancelTick,
                "hspeedAtConfirm", recorder.horizontalSpeed, "status", String.valueOf(onGame(() -> conductor.operationStatus(op))),
                "resource", String.valueOf(onGame(conductor::resourceStatus)));

        // 2. reuse on evidence; let it run to the end without cancel
        recorder.phase = "S4 reuse";
        BlockPos here = onGame(() -> mc.player.blockPosition());
        OperationResult reuse = onGame(() -> conductor.requestTravel(NavigationGoal.getToBlock(here.getX() - 30, here.getY(), here.getZ())));
        event("S4 reuse requestTravel", "result", String.valueOf(reuse));
        OperationId op2 = reuse.status().map(st -> st.id()).orElse(null);
        recorder.operation = op2;
        boolean ended = waitOperation(op2, OperationState.UNCERTAIN, 1500);
        event("S4 reuse ended", "ok", ended, "status", String.valueOf(op2 == null ? null : onGame(() -> conductor.operationStatus(op2))),
                "pos", String.valueOf(onGame(() -> mc.player.blockPosition())), "target", String.valueOf(here.offset(-30, 0, 0)));
        waitResource(ResourceState.AVAILABLE, 400);

        // 3. a foreign process holds the resource: grant refused, interference detected
        recorder.phase = "S4 mining";
        cmd("fill " + (here.getX() - 25) + " " + here.getY() + " " + (here.getZ() + 4) + " " + (here.getX() - 23) + " "
                + (here.getY() + 2) + " " + (here.getZ() + 6) + " minecraft:oak_log");
        onGame(() -> {
            baritone.getMineProcess().mineByName(0, "oak_log");
            return null;
        });
        waitTicks(40);
        event("S4 request while mining", "result", String.valueOf(onGame(() -> conductor.requestTravel(NavigationGoal.xz(0, 0)))),
                "resource", String.valueOf(onGame(conductor::resourceStatus)));
        onGame(() -> {
            baritone.getMineProcess().cancel();
            return null;
        });
        event("S4 mining stopped; resource available again", "ok", waitResource(ResourceState.AVAILABLE, 600));

        // 4. water: cancel takes effect at the edge, then drowning protection takes over (detected)
        recorder.phase = "S4 water";
        cmd("fill 10 -63 55 25 -61 65 minecraft:water");
        cmd("tp @s 0.5 -60 60.5");
        waitTicks(20);
        waitResource(ResourceState.AVAILABLE, 400);
        OperationResult swim = onGame(() -> conductor.requestTravel(NavigationGoal.getToBlock(35, -60, 60)));
        OperationId op3 = swim.status().map(st -> st.id()).orElse(null);
        recorder.operation = op3;
        waitTicks(15);
        onGame(() -> conductor.cancel(op3));
        boolean swimCancelled = waitOperation(op3, OperationState.CANCELLED, 600);
        event("S4 water cancel", "cancelled", swimCancelled, "status", String.valueOf(onGame(() -> conductor.operationStatus(op3))));
        boolean quarantinedAgain = waitResource(ResourceState.QUARANTINED, 600);
        event("S4 water later activity", "quarantined", quarantinedAgain, "resource", String.valueOf(onGame(conductor::resourceStatus)));
        cmd("tp @s 0.5 -60 0.5");
        waitResource(ResourceState.AVAILABLE, 600);
        Observation<Boolean> beforeDimensionChange = onGame(() ->
                conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, FreshnessPolicy.maxAge(OptionalLong.of(100_000), Optional.empty())))
                .observation().orElseThrow();

        // ---- Dimension change: context and authority invalidation, live ----
        recorder.phase = "D";
        event("D teleport to nether roof");
        onGame(() -> {
            mc.player.connection.sendCommand("execute in minecraft:the_nether run tp @s 0.5 128 0.5");
            return null;
        });
        long dimDeadline = System.currentTimeMillis() + 30_000;
        while (!onGame(() -> Compat.dimension(mc.level)).equals("minecraft:the_nether")) {
            if (System.currentTimeMillis() > dimDeadline) {
                event("D dimension never changed (is the player op?)");
                break;
            }
            Thread.sleep(200);
        }
        waitTicks(40);
        event("D revalidate pre-change observation", "result",
                String.valueOf(onGame(() -> conductor.revalidate(beforeDimensionChange, FreshnessPolicy.maxAge(OptionalLong.of(100_000), Optional.empty())))));
        event("D resource right after change", "resource", String.valueOf(onGame(conductor::resourceStatus)));
        event("D fresh evidence in new context", "ok", waitResource(ResourceState.AVAILABLE, 400),
                "resource", String.valueOf(onGame(conductor::resourceStatus)));
        event("D discover", "context", String.valueOf(onGame(conductor::discover).context()));

        dumpSink(sink);
    }

    private void dumpSink(InMemoryDiagnosticSink sink) {
        for (DiagnosticEvent e : sink.snapshot()) {
            log.write(JsonLog.obj("type", "sink", "event", e.type().name(), "thread", e.thread(), "tick", e.tick(),
                    "context", String.valueOf(e.context()), "subject", e.subject(), "detail", e.detail()));
        }
    }

    private boolean waitResource(ResourceState wanted, int maxTicks) throws InterruptedException {
        long end = recorder.ticks + maxTicks;
        while (recorder.ticks < end) {
            if (recorder.resourceState.equals(wanted.name())) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    /** Waits for the recorder (which tracks {@code recorder.operation} each tick) to report {@code wanted}. */
    private boolean waitOperation(OperationId id, OperationState wanted, int maxTicks) throws InterruptedException {
        if (id == null) {
            return false;
        }
        long end = recorder.ticks + maxTicks;
        while (recorder.ticks < end) {
            if (recorder.operationState.equals(wanted.name())) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    void cmd(String command) throws Exception {
        onGame(() -> {
            mc.player.connection.sendCommand(command);
            return null;
        });
        waitTicks(2);
    }

    boolean waitIdle(int consecutive, int maxTicks) throws InterruptedException {
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

    void waitTicks(long n) throws InterruptedException {
        long target = recorder.ticks + n;
        while (recorder.ticks < target) {
            Thread.sleep(10);
        }
    }

    <T> T onGame(Supplier<T> work) throws Exception {
        return mc.submit(work).get(60, TimeUnit.SECONDS);
    }

    void event(String name, Object... kv) {
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
