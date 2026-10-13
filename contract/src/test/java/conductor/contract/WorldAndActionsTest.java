package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Block queries and one-shot actions: lease rules, engine-conflict rule, validation, and honest "not loaded". */
class WorldAndActionsTest {

    private static final int WINDOW = 3;

    private FakeRuntime runtime;
    private FakeActionPort actions;
    private InMemoryDiagnosticSink sink;
    private ContractService core;
    private ModuleHost host;
    private ModuleContext ctx;
    private final Map<BlockPoint, String> blocks = new HashMap<>();

    @BeforeEach
    void setUp() {
        runtime = new FakeRuntime();
        runtime.port = new FakePort();
        actions = new FakeActionPort();
        runtime.actionPort = actions;
        runtime.worldPort = new WorldPort() {
            public Optional<String> blockAt(BlockPoint at) { return Optional.ofNullable(blocks.get(at)); }
            public List<BlockPoint> findBlocks(Set<String> ids, BlockPoint center, int radius, int limit) {
                List<BlockPoint> found = new ArrayList<>();
                blocks.forEach((p, b) -> {
                    if (ids.contains(b) && Math.abs(p.x() - center.x()) <= radius && Math.abs(p.y() - center.y()) <= radius
                            && Math.abs(p.z() - center.z()) <= radius) found.add(p);
                });
                found.sort(Comparator.comparingInt(p -> Math.abs(p.x() - center.x()) + Math.abs(p.y() - center.y()) + Math.abs(p.z() - center.z())));
                return found.subList(0, Math.min(limit, found.size()));
            }
        };
        runtime.quiet();
        runtime.samples.put(ObservationKinds.PLAYER_POSITION, Sample.observed(new BlockPoint(0, 64, 0)));
        sink = new InMemoryDiagnosticSink(10_000);
        ContextTracker contexts = new ContextTracker(sink);
        core = new ContractService(runtime, contexts, sink, new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.empty()));
        contexts.onInWorld("minecraft:overworld", false, 0);
        host = new ModuleHost(core);
        ModuleContext[] holder = new ModuleContext[1];
        host.register("tester", new ConductorModule() {
            public ReleasePolicy releasePolicy() { return ReleasePolicy.none(); }
            public void onReady(ModuleContext c) { holder[0] = c; }
        });
        ticks(WINDOW);
        ctx = holder[0];
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            runtime.tick++;
            core.tick();
            host.tick();
        }
    }

    @Test
    void actionsNeedTheirLease() {
        assertEquals(Optional.of(RejectionReason.LEASE_REQUIRED), Optional.ofNullable(ctx.selectHotbar(3).rejection()));
        assertEquals(Optional.of(RejectionReason.LEASE_REQUIRED), Optional.ofNullable(ctx.sendChat("hi").rejection()));
        assertTrue(actions.calls.isEmpty());

        assertTrue(ctx.acquire(Resources.HOTBAR).isGranted());
        assertTrue(ctx.selectHotbar(3).isAccepted());
        assertEquals(List.of("hotbar 3"), actions.calls);
    }

    @Test
    void invalidArgumentsNeverReachTheGame() {
        ctx.acquire(Resources.HOTBAR);
        ctx.acquire(Resources.LOOK);
        ctx.acquire(Resources.CHAT);
        assertEquals(RejectionReason.INVALID_ARGUMENT, ctx.selectHotbar(9).rejection());
        assertEquals(RejectionReason.INVALID_ARGUMENT, ctx.look(0, 91).rejection());
        assertEquals(RejectionReason.INVALID_ARGUMENT, ctx.look(Double.NaN, 0).rejection());
        assertEquals(RejectionReason.INVALID_ARGUMENT, ctx.sendChat(" ").rejection());
        assertEquals(RejectionReason.INVALID_ARGUMENT, ctx.sendChat("x".repeat(257)).rejection());
        assertEquals(RejectionReason.INVALID_ARGUMENT, ctx.sendChat("colour §c").rejection());
        assertTrue(actions.calls.isEmpty());
    }

    @Test
    void actionsTheEngineWouldOverwriteWaitUntilTheBotIsFreeButChatDoesNot() {
        ctx.acquire(Resources.LOOK);
        ctx.acquire(Resources.CHAT);
        assertTrue(ctx.requestTravel(NavigationGoal.xz(10, 10)).isAccepted());
        runtime.busy(FakePort.OWN);
        ticks(1);
        assertEquals(RejectionReason.RESOURCE_NOT_RELEASED, ctx.look(90, 0).rejection());
        assertTrue(ctx.sendChat("on my way").isAccepted());
        assertEquals(List.of("chat on my way"), actions.calls);
    }

    @Test
    void chatIsLimitedToOneMessagePerTick() {
        ctx.acquire(Resources.CHAT);
        assertTrue(ctx.sendChat("one").isAccepted());
        assertEquals(RejectionReason.RATE_LIMITED, ctx.sendChat("two").rejection());
        ticks(1);
        assertTrue(ctx.sendChat("three").isAccepted());
        assertEquals(List.of("chat one", "chat three"), actions.calls);
    }

    @Test
    void aMissingTargetIsReportedAndEveryActionIsRecorded() {
        ctx.acquire(Resources.INTERACT);
        actions.refuse = "no entity 42 in reach";
        ActionResult r = ctx.attack(42);
        assertEquals(RejectionReason.TARGET_UNAVAILABLE, r.rejection());
        assertTrue(ctx.useItem(true).isAccepted());
        assertEquals(2, sink.snapshot().stream().filter(e -> e.type() == DiagnosticEvent.Type.ACTION).count());
    }

    @Test
    void aPreemptedHolderLosesItsActions() {
        ctx.acquire(Resources.LOOK);
        ModuleContext[] guard = new ModuleContext[1];
        host.register("guard", new ConductorModule() {
            public ReleasePolicy releasePolicy() { return ReleasePolicy.none(); }
            public Priority priority() { return Priority.SAFETY; }
            public void onReady(ModuleContext c) { guard[0] = c; }
        });
        ticks(1);
        assertTrue(guard[0].acquire(Resources.LOOK).isGranted(), "non-evidence resources hand over at once");
        assertEquals(RejectionReason.LEASE_REQUIRED, ctx.look(0, 0).rejection());
        assertTrue(guard[0].look(0, 0).isAccepted());
    }

    @Test
    void blockQueriesSayWhenAChunkIsNotLoaded() {
        blocks.put(new BlockPoint(1, 64, 0), "minecraft:stone");
        assertEquals(Optional.of("minecraft:stone"), ctx.blockAt(new BlockPoint(1, 64, 0)).block());
        BlockReading unloaded = ctx.blockAt(new BlockPoint(500, 64, 0));
        assertTrue(unloaded.isAccepted());
        assertTrue(unloaded.block().isEmpty(), "unloaded is empty, never air");
    }

    @Test
    void blockSearchIsBoundedAndNearestFirst() {
        blocks.put(new BlockPoint(5, 64, 0), "minecraft:diamond_ore");
        blocks.put(new BlockPoint(2, 64, 0), "minecraft:diamond_ore");
        blocks.put(new BlockPoint(40, 64, 0), "minecraft:diamond_ore");
        BlockSearch s = ctx.findBlocks(Set.of("minecraft:diamond_ore"), 16, 10);
        assertEquals(List.of(new BlockPoint(2, 64, 0), new BlockPoint(5, 64, 0)), s.found());
        assertEquals(RejectionReason.INVALID_ARGUMENT, ctx.findBlocks(Set.of("minecraft:stone"), 17, 10).rejection());
        assertEquals(RejectionReason.INVALID_ARGUMENT, ctx.findBlocks(Set.of(), 4, 10).rejection());
    }

    @Test
    void aRuntimeWithoutActionsSaysSo() {
        runtime.actionPort = null;
        runtime.worldPort = null;
        ctx.acquire(Resources.HOTBAR);
        assertEquals(RejectionReason.NO_ACTIONS, ctx.selectHotbar(1).rejection());
        assertEquals(RejectionReason.NO_ACTIONS, ctx.blockAt(new BlockPoint(0, 0, 0)).rejection());
    }

    @Test
    void offThreadActionsTouchNothing() throws InterruptedException {
        ctx.acquire(Resources.HOTBAR);
        ActionResult[] r = new ActionResult[1];
        Thread t = new Thread(() -> r[0] = ctx.selectHotbar(1));
        t.start();
        t.join();
        assertEquals(RejectionReason.OFF_THREAD, r[0].rejection());
        assertTrue(actions.calls.isEmpty());
    }
}
