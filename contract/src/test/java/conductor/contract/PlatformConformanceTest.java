package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module platform conformance (docs/MODULE-PLATFORM.md): many modules side by side, leases with priority preemption,
 * evidence-gated MOVEMENT hand-over, topics, services, lifecycle, failure isolation.
 */
class PlatformConformanceTest {

    private static final int WINDOW = 3;
    private static final Topic<String> CHAT = new Topic<>("test:chat", String.class);
    private static final ServiceKey<Runnable> PING = new ServiceKey<>("test:ping", Runnable.class);

    private FakeRuntime runtime;
    private FakePort port;
    private InMemoryDiagnosticSink sink;
    private ContextTracker contexts;
    private ContractService core;
    private ModuleHost host;

    /** A module whose behaviour each test fills in. */
    static final class TestModule implements ConductorModule {
        Priority priority = Priority.NORMAL;
        Set<ServiceKey<?>> requires = Set.of();
        Consumer<ModuleContext> ready = c -> { };
        Consumer<ModuleContext> tick = c -> { };
        final List<String> log = new ArrayList<>();
        ModuleContext ctx;
        BiConsumer<Lease, ModuleId> revoked = (l, by) -> { };

        @Override
        public ReleasePolicy releasePolicy() {
            return new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.empty());
        }

        @Override
        public void onReady(ModuleContext conductor) {
            ctx = conductor;
            log.add("ready");
            ready.accept(conductor);
        }

        @Override
        public void onTick(ModuleContext conductor) {
            tick.accept(conductor);
        }

        @Override
        public Priority priority() {
            return priority;
        }

        @Override
        public Set<ServiceKey<?>> requires() {
            return requires;
        }

        @Override
        public void onLeaseGranted(ModuleContext conductor, Lease lease) {
            log.add("granted " + lease.resource());
        }

        @Override
        public void onLeaseRevoked(ModuleContext conductor, Lease lease, ModuleId by) {
            log.add("revoked " + lease.resource() + " by " + by);
            revoked.accept(lease, by);
        }

        @Override
        public void onDisable(ModuleContext conductor) {
            log.add("disabled");
        }
    }

    @BeforeEach
    void setUp() {
        runtime = new FakeRuntime();
        port = new FakePort();
        runtime.port = port;
        runtime.quiet();
        sink = new InMemoryDiagnosticSink(10_000);
        contexts = new ContextTracker(sink);
        core = new ContractService(runtime, contexts, sink, new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.empty()));
        host = new ModuleHost(core);
        contexts.onInWorld("minecraft:overworld", false, 0);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            runtime.tick++;
            core.tick();
            host.tick();
        }
    }

    private List<DiagnosticEvent> events(DiagnosticEvent.Type type) {
        return sink.snapshot().stream().filter(e -> e.type() == type).toList();
    }

    // --- lifecycle and services ---

    @Test
    void modulesStartInDependencyOrder() {
        TestModule consumer = new TestModule();
        consumer.requires = Set.of(PING);
        TestModule provider = new TestModule();
        provider.ready = c -> c.provide(PING, () -> { });
        ModuleId consumerId = host.register("consumer", consumer);
        ModuleId providerId = host.register("provider", provider);

        ticks(1);

        assertEquals(Optional.of(ModuleState.RUNNING), host.state(providerId));
        assertEquals(Optional.of(ModuleState.RUNNING), host.state(consumerId), "started in the same tick once PING appeared");
        assertTrue(consumer.ctx.service(PING).isPresent());
    }

    @Test
    void missingServiceKeepsAModuleBlocked() {
        TestModule consumer = new TestModule();
        consumer.requires = Set.of(PING);
        ModuleId id = host.register("consumer", consumer);
        ticks(5);

        assertEquals(Optional.of(ModuleState.BLOCKED), host.state(id));
        assertTrue(consumer.log.isEmpty(), "never started");
    }

    @Test
    void disablingAProviderBlocksItsDependentsAndEnablingRestoresThem() {
        TestModule provider = new TestModule();
        provider.ready = c -> c.provide(PING, () -> { });
        TestModule consumer = new TestModule();
        consumer.requires = Set.of(PING);
        ModuleId p = host.register("provider", provider);
        ModuleId c = host.register("consumer", consumer);
        ticks(1);

        ModuleManagement mgmt = host.management();
        assertTrue(mgmt.disable(p, "test"));
        ticks(1);
        assertEquals(Optional.of(ModuleState.DISABLED), host.state(p));
        assertEquals(Optional.of(ModuleState.BLOCKED), host.state(c));
        assertTrue(provider.log.contains("disabled"));
        assertTrue(consumer.log.contains("disabled"));

        assertTrue(mgmt.enable(p));
        ticks(1);
        assertEquals(Optional.of(ModuleState.RUNNING), host.state(p));
        assertEquals(Optional.of(ModuleState.RUNNING), host.state(c));
        assertEquals(2, provider.log.stream().filter("ready"::equals).count(), "onReady again after enable");
    }

    @Test
    void managementServiceIsAvailableToModules() {
        TestModule m = new TestModule();
        host.register("m", m);
        ticks(1);

        List<ModuleInfo> modules = m.ctx.service(ModuleManagement.KEY).orElseThrow().modules();
        assertEquals(1, modules.size());
        assertEquals(ModuleState.RUNNING, modules.get(0).state());
    }

    // --- failure isolation ---

    @Test
    void aModuleThatThrowsFailsAloneAndLosesItsLeases() {
        TestModule good = new TestModule();
        TestModule bad = new TestModule();
        bad.ready = c -> c.acquire(Resources.LOOK);
        bad.tick = c -> {
            throw new IllegalStateException("bug");
        };
        ModuleId g = host.register("good", good);
        ModuleId b = host.register("bad", bad);
        ticks(2);

        assertEquals(Optional.of(ModuleState.FAILED), host.state(b));
        assertEquals(Optional.of(ModuleState.RUNNING), host.state(g));
        assertTrue(good.ctx.leases().isEmpty(), "the failed module's lease was revoked");
        assertFalse(events(DiagnosticEvent.Type.MODULE_FAULT).isEmpty());
        assertTrue(good.ctx.acquire(Resources.LOOK).isGranted());
    }

    @Test
    void aStoppedModulesContextCanNoLongerAct() {
        TestModule m = new TestModule();
        ModuleId id = host.register("m", m);
        ticks(1);
        host.management().disable(id, "test");

        assertEquals(LeaseResult.Status.DENIED, m.ctx.acquire(Resources.LOOK).status());
        assertEquals(Optional.of(RejectionReason.AUTHORITY_INVALIDATED), m.ctx.requestTravel(NavigationGoal.xz(1, 1)).rejection());
        assertEquals(0, port.engineCalls());
    }

    // --- leases without evidence signals ---

    @Test
    void equalPriorityIsDeniedHigherPriorityPreemptsImmediately() {
        TestModule a = new TestModule();
        TestModule b = new TestModule();
        TestModule urgent = new TestModule();
        urgent.priority = Priority.HIGH;
        host.register("a", a);
        host.register("b", b);
        host.register("urgent", urgent);
        ticks(1);

        assertTrue(a.ctx.acquire(Resources.LOOK).isGranted());
        LeaseResult denied = b.ctx.acquire(Resources.LOOK);
        assertEquals(LeaseResult.Status.DENIED, denied.status());
        assertEquals(Optional.of(a.ctx.self()), denied.holder());

        LeaseResult taken = urgent.ctx.acquire(Resources.LOOK);
        assertEquals(LeaseResult.Status.GRANTED, taken.status());
        assertTrue(a.log.contains("revoked LOOK by urgent"));
        assertEquals(urgent.ctx.self(), a.ctx.leases().get(0).holder());
    }

    @Test
    void releaseFreesTheResource() {
        TestModule a = new TestModule();
        TestModule b = new TestModule();
        host.register("a", a);
        host.register("b", b);
        ticks(1);

        Lease lease = a.ctx.acquire(Resources.HOTBAR).lease().orElseThrow();
        a.ctx.release(lease);
        assertTrue(b.ctx.acquire(Resources.HOTBAR).isGranted());
    }

    // --- MOVEMENT: leases meet release evidence ---

    @Test
    void travelTakesAScopedMovementLeaseThatEndsWithTheOperation() {
        TestModule walker = new TestModule();
        TestModule other = new TestModule();
        host.register("walker", walker);
        host.register("other", other);
        ticks(WINDOW);

        OperationId op = walker.ctx.requestTravel(NavigationGoal.xz(10, 0)).status().orElseThrow().id();
        runtime.busy(FakePort.OWN);
        ticks(2);
        assertEquals(walker.ctx.self(), walker.ctx.leases().get(0).holder());
        assertTrue(walker.ctx.leases().get(0).scoped());
        assertEquals(Optional.of(RejectionReason.LEASE_HELD), other.ctx.requestTravel(NavigationGoal.xz(0, 0)).rejection());
        assertEquals(1, port.calls.stream().filter(c -> c.startsWith("travel")).count(), "no engine call for the refused request");

        walker.ctx.cancel(op);
        runtime.quiet();
        ticks(WINDOW + 1);
        assertTrue(walker.ctx.leases().isEmpty(), "scoped lease ended with the operation");
        assertTrue(other.ctx.requestTravel(NavigationGoal.xz(0, 0)).isAccepted());
    }

    @Test
    void otherModulesCannotCancelOrInspectYourOperation() {
        TestModule walker = new TestModule();
        TestModule other = new TestModule();
        host.register("walker", walker);
        host.register("other", other);
        ticks(WINDOW);
        OperationId op = walker.ctx.requestTravel(NavigationGoal.xz(10, 0)).status().orElseThrow().id();

        assertEquals(Optional.of(RejectionReason.UNKNOWN_OPERATION), other.ctx.cancel(op).rejection());
        assertEquals(Optional.of(RejectionReason.UNKNOWN_OPERATION), other.ctx.operationStatus(op).rejection());
        assertEquals(0, port.calls.stream().filter("cancel"::equals).count());
    }

    @Test
    void higherPriorityPreemptsMovementButWaitsForReleaseEvidence() {
        TestModule walker = new TestModule();
        TestModule guard = new TestModule();
        guard.priority = Priority.SAFETY;
        host.register("walker", walker);
        host.register("guard", guard);
        ticks(WINDOW);
        OperationId op = walker.ctx.requestTravel(NavigationGoal.xz(10, 0)).status().orElseThrow().id();
        runtime.busy(FakePort.OWN);
        ticks(2);

        LeaseResult r = guard.ctx.acquire(Resources.MOVEMENT);
        assertEquals(LeaseResult.Status.PENDING, r.status());
        assertTrue(walker.log.contains("revoked MOVEMENT by guard"));
        assertEquals(1, port.calls.stream().filter("cancel"::equals).count(), "walker's travel cancelled best-effort");
        assertEquals(OperationState.CANCELLING, walker.ctx.operationStatus(op).status().orElseThrow().state());

        ticks(20); // the engine keeps running the segment (live finding L1)
        assertFalse(guard.log.contains("granted MOVEMENT"), "no hand-over while the engine still steers");

        runtime.quiet();
        ticks(WINDOW);
        assertTrue(guard.log.contains("granted MOVEMENT"));
        assertEquals(guard.ctx.self(), guard.ctx.leases().get(0).holder());
        assertTrue(guard.ctx.requestTravel(NavigationGoal.xz(-5, 0)).isAccepted(), "guard can move now");
    }

    @Test
    void movementRequestWhileEngineIsBusyWithForeignActivityIsPending() {
        TestModule m = new TestModule();
        host.register("m", m);
        runtime.busy("engine.MineProcess");
        ticks(WINDOW + 2);

        assertEquals(LeaseResult.Status.PENDING, m.ctx.acquire(Resources.MOVEMENT).status());
        runtime.quiet();
        ticks(WINDOW);
        assertTrue(m.log.contains("granted MOVEMENT"));
    }

    @Test
    void declaredEngineProcessesAreNotInterference() {
        TestModule bridge = new TestModule(); // e.g. a native TenorClef bridge driving MineProcess itself
        host.register("bridge", bridge);
        ticks(WINDOW);
        assertTrue(bridge.ctx.acquire(Resources.MOVEMENT).isGranted());
        bridge.ctx.declareEngineProcesses(Set.of("engine.MineProcess"));
        int before = events(DiagnosticEvent.Type.INTERFERENCE).size();

        runtime.busy("engine.MineProcess");
        ticks(10);
        assertEquals(before, events(DiagnosticEvent.Type.INTERFERENCE).size());

        runtime.busy("engine.ExploreProcess");
        ticks(1);
        assertEquals(before + 1, events(DiagnosticEvent.Type.INTERFERENCE).size(), "undeclared processes still are");
    }

    // --- topics ---

    @Test
    void topicsDeliverAtTheEndOfTheTickInOrder() {
        TestModule sender = new TestModule();
        TestModule receiver = new TestModule();
        List<String> got = new ArrayList<>();
        receiver.ready = c -> c.subscribe(CHAT, got::add);
        sender.tick = c -> {
            c.publish(CHAT, "one");
            c.publish(CHAT, "two");
            got.add("(sender still in its tick)");
        };
        host.register("receiver", receiver);
        host.register("sender", sender);
        ticks(1);

        assertEquals(List.of("(sender still in its tick)", "one", "two"), got);
    }

    @Test
    void aThrowingSubscriberFailsAloneAndOthersStillReceive() {
        TestModule sender = new TestModule();
        TestModule bad = new TestModule();
        TestModule good = new TestModule();
        List<String> got = new ArrayList<>();
        bad.ready = c -> c.subscribe(CHAT, m -> {
            throw new IllegalStateException("bad handler");
        });
        good.ready = c -> c.subscribe(CHAT, got::add);
        sender.tick = c -> c.publish(CHAT, "hello");
        host.register("bad", bad);
        host.register("good", good);
        ModuleId s = host.register("sender", sender);
        ticks(2);

        assertEquals(Optional.of(ModuleState.FAILED), host.state(bad.ctx.self()));
        assertEquals(Optional.of(ModuleState.RUNNING), host.state(s));
        assertEquals(List.of("hello", "hello"), got);
    }

    @Test
    void leaseChangesArePublished() {
        TestModule watcher = new TestModule();
        TestModule a = new TestModule();
        List<LeaseEvent> seen = new ArrayList<>();
        watcher.ready = c -> c.subscribe(Topics.LEASES, seen::add);
        host.register("watcher", watcher);
        host.register("a", a);
        ticks(1);
        a.ctx.acquire(Resources.CHAT);
        ticks(1);

        assertEquals(LeaseEvent.Kind.GRANTED, seen.get(seen.size() - 1).kind());
        assertEquals(Resources.CHAT, seen.get(seen.size() - 1).lease().resource());
    }

    @Test
    void publishStormsAreCappedPerTick() {
        TestModule loud = new TestModule();
        TestModule ear = new TestModule();
        int[] count = {0};
        ear.ready = c -> c.subscribe(CHAT, m -> count[0]++);
        loud.ready = c -> {
            for (int i = 0; i < ModuleHost.MAX_DELIVERIES_PER_TICK + 500; i++) {
                c.publish(CHAT, "x");
            }
        };
        host.register("ear", ear);
        host.register("loud", loud);
        ticks(1);
        assertEquals(ModuleHost.MAX_DELIVERIES_PER_TICK, count[0]);
        ticks(1);
        assertEquals(ModuleHost.MAX_DELIVERIES_PER_TICK + 500, count[0], "the rest arrives next tick");
    }

    // --- threading and policy ---

    @Test
    void moduleCallsFromAnotherThreadDoNothing() throws InterruptedException {
        TestModule m = new TestModule();
        host.register("m", m);
        ticks(WINDOW);
        AtomicReference<LeaseResult> lease = new AtomicReference<>();
        AtomicReference<OperationResult> travel = new AtomicReference<>();
        Thread t = new Thread(() -> {
            lease.set(m.ctx.acquire(Resources.LOOK));
            travel.set(m.ctx.requestTravel(NavigationGoal.xz(1, 1)));
            m.ctx.publish(CHAT, "x");
        }, "worker");
        t.start();
        t.join();

        assertEquals(LeaseResult.Status.DENIED, lease.get().status());
        assertEquals(Optional.of(RejectionReason.OFF_THREAD), travel.get().rejection());
        assertEquals(0, port.engineCalls());
        assertTrue(m.ctx.leases().isEmpty());
    }

    @Test
    void combinedPolicyUsesTheLongestWindows() {
        ConductorModule a = new TestModule();
        ConductorModule b = new ConductorModule() {
            @Override
            public ReleasePolicy releasePolicy() {
                return new ReleasePolicy(OptionalLong.of(40), OptionalLong.of(600));
            }

            @Override
            public void onReady(ModuleContext conductor) {
            }
        };
        ReleasePolicy p = ModuleHost.combinedPolicy(List.of(a, b));
        assertEquals(OptionalLong.of(40), p.confirmStopWindowTicks());
        assertEquals(OptionalLong.of(600), p.uncertainAfterTicks());
    }

    @Test
    void duplicateIdsAreMadeUnique() {
        ModuleId first = host.register("same", new TestModule());
        ModuleId second = host.register("same", new TestModule());
        assertEquals("same", first.value());
        assertEquals("same#2", second.value());
    }
}
