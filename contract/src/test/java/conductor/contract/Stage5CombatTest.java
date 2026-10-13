package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 5: combat delegation. Combat steers the movement resource, so it follows the travel rules (grant only on
 * AVAILABLE, stop only on evidence, exclusive with travel), and the use key, which engine combat holds outside
 * FORCED_INPUTS, is part of the release evidence.
 */
class Stage5CombatTest {

    private static final int WINDOW = 5;

    private FakeRuntime runtime;
    private FakePort travelPort;
    private FakeCombatPort combatPort;
    private InMemoryDiagnosticSink sink;
    private ContractService conductor;

    @BeforeEach
    void setUp() {
        runtime = new FakeRuntime();
        travelPort = new FakePort();
        combatPort = new FakeCombatPort();
        runtime.port = travelPort;
        runtime.combatPort = combatPort;
        runtime.reportsUseKey = true;
        runtime.quiet();
        sink = new InMemoryDiagnosticSink(10_000);
        ContextTracker contexts = new ContextTracker(sink);
        conductor = new ContractService(runtime, contexts, sink, new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.of(200)));
        contexts.onInWorld("minecraft:overworld", false, 0);
        ticks(WINDOW);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            runtime.tick++;
            conductor.tick();
        }
    }

    private void fighting(boolean shieldUp) {
        runtime.busy(FakeCombatPort.OWN);
        runtime.samples.put(ObservationKinds.USE_KEY_HELD, Sample.observed(shieldUp));
    }

    private OperationId fight() {
        OperationResult r = conductor.requestCombat(CombatGoal.hostiles());
        assertTrue(r.isAccepted(), r::toString);
        fighting(true);
        return r.status().orElseThrow().id();
    }

    private OperationState state(OperationId id) {
        return conductor.operationStatus(id).status().orElseThrow().state();
    }

    @Test
    void combatIsDelegatedOnlyWhileTheResourceIsAvailable() {
        OperationId id = fight();
        assertEquals(List.of("engage CombatGoal[kind=HOSTILES, name=null]"), combatPort.calls);
        ticks(3);
        assertEquals(OperationState.EXECUTING, state(id));
        assertTrue(sink.snapshot().stream().noneMatch(e -> e.type() == DiagnosticEvent.Type.INTERFERENCE),
                "the combat process is the operation's own controller, not interference");
    }

    @Test
    void combatAndTravelExcludeEachOther() {
        fight();
        assertEquals(Optional.of(RejectionReason.CONFLICTING_OPERATION),
                conductor.requestTravel(NavigationGoal.xz(10, 10)).rejection());
        assertTrue(travelPort.calls.isEmpty());
    }

    @Test
    void aHeldUseKeyAloneKeepsTheResourceFromBeingReleased() {
        OperationId id = fight();
        conductor.cancel(id);
        runtime.quiet();
        runtime.samples.put(ObservationKinds.USE_KEY_HELD, Sample.observed(true)); // shield still up
        ticks(WINDOW * 3);
        assertEquals(OperationState.STOPPED_PATHING, state(id));
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());

        runtime.samples.put(ObservationKinds.USE_KEY_HELD, Sample.observed(false));
        ticks(WINDOW);
        assertEquals(OperationState.CANCELLED, state(id));
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
        assertEquals(List.of("engage CombatGoal[kind=HOSTILES, name=null]", "cancel"), combatPort.calls);
    }

    @Test
    void aRuntimeThatOffersCombatMustReportTheUseKey() {
        runtime.reportsUseKey = false; // combat offered, USE_KEY_HELD not provided: never quiet
        ticks(WINDOW * 3);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
        assertTrue(conductor.resourceStatus().detail().contains("USE_KEY_HELD"), conductor.resourceStatus().detail());
    }

    @Test
    void aRuntimeWithoutCombatRefusesWithNoCombatAndTravelIsUnchanged() {
        runtime.combatPort = null;
        runtime.reportsUseKey = false;
        runtime.samples.remove(ObservationKinds.USE_KEY_HELD);
        ticks(WINDOW);
        assertEquals(Optional.of(RejectionReason.NO_COMBAT), conductor.requestCombat(CombatGoal.players()).rejection());
        assertTrue(conductor.requestTravel(NavigationGoal.xz(10, 10)).isAccepted(),
                "without combat, release does not need the use key (Stage 4 behaviour unchanged)");
    }

    @Test
    void anotherProcessTakingOverDuringCombatIsDetected() {
        OperationId id = fight();
        runtime.busy("engine.MineProcess");
        ticks(1);
        assertEquals(OperationState.UNCERTAIN, state(id));
    }

    @Test
    void engineRefusalSpendsNothingButQuarantines() {
        combatPort.next = new NavigationPort.Delegation(false, "engine-pvp", true, "no player");
        assertEquals(Optional.of(RejectionReason.ENGINE_DECLINED), conductor.requestCombat(CombatGoal.player("Steve")).rejection());
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
    }

    @Test
    void goalsValidateTheirName() {
        assertThrows(IllegalArgumentException.class, () -> CombatGoal.player(" "));
        assertThrows(IllegalArgumentException.class, () -> new CombatGoal(CombatGoal.Kind.HOSTILES, "x"));
        assertEquals(CombatGoal.Kind.MOB, CombatGoal.mob("minecraft:zombie").kind());
    }

    @Test
    void offThreadCombatRequestsTouchNothing() throws InterruptedException {
        Thread t = new Thread(() -> assertEquals(Optional.of(RejectionReason.OFF_THREAD),
                conductor.requestCombat(CombatGoal.hostiles()).rejection()));
        t.start();
        t.join();
        assertTrue(combatPort.calls.isEmpty());
        assertFalse(sink.snapshot().stream().noneMatch(e -> e.type() == DiagnosticEvent.Type.OFF_THREAD_VIOLATION));
    }

    @Test
    void aModuleFightsUnderAScopedMovementLease() {
        ModuleHost host = new ModuleHost(conductor);
        List<String> log = new java.util.ArrayList<>();
        ModuleContext[] ctx = new ModuleContext[1];
        host.register("fighter", new ConductorModule() {
            public ReleasePolicy releasePolicy() { return ReleasePolicy.none(); }
            public void onReady(ModuleContext c) {
                ctx[0] = c;
                log.add("fight " + c.requestCombat(CombatGoal.hostiles()).isAccepted());
            }
        });
        host.tick();
        fighting(true);
        assertEquals(List.of("fight true"), log);
        List<Lease> held = ctx[0].leases();
        assertEquals(1, held.size());
        assertEquals(Resources.MOVEMENT, held.get(0).resource());
        assertTrue(held.get(0).scoped(), "taken for the combat operation only");
    }

    @Test
    void anOwnerCanStillReadHowItsOperationEndedAfterANewOneBegan() {
        OperationResult walk = conductor.requestTravel(NavigationGoal.xz(10, 10));
        OperationId trip = walk.status().orElseThrow().id();
        conductor.cancel(trip);
        ticks(WINDOW);                                         // fake engine already quiet: CANCELLED
        assertEquals(OperationState.CANCELLED, state(trip));
        fight();                                               // a new operation replaces the current one
        assertEquals(OperationState.CANCELLED, state(trip), "the trip's final state is kept for its owner");
        assertEquals(Optional.of(RejectionReason.AUTHORITY_INVALIDATED), conductor.cancel(trip).rejection());
    }
}

