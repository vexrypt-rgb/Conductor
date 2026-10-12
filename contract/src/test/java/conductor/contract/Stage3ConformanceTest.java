package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 3 conformance tests, under the Stage 4 grant rule (a grant needs release evidence): cancellation requested
 * vs confirmed, context invalidation vs resource release, identity change vs disappearance, no new grant while the
 * resource is unsafe, off-thread calls not starting movement, contract-level exclusivity, and sink evidence.
 */
class Stage3ConformanceTest {

    static final NavigationGoal GOAL = NavigationGoal.getToBlock(10, 64, -5);
    static final ReleasePolicy POLICY = new ReleasePolicy(OptionalLong.of(3), OptionalLong.empty());

    private FakeRuntime runtime;
    private FakePort port;
    private InMemoryDiagnosticSink sink;
    private ContextTracker contexts;
    private ContractService conductor;

    @BeforeEach
    void setUp() {
        runtime = new FakeRuntime();
        port = new FakePort();
        runtime.port = port;
        runtime.quiet();
        sink = new InMemoryDiagnosticSink(1000);
        contexts = new ContextTracker(sink);
        conductor = new ContractService(runtime, contexts, sink, POLICY);
        contexts.onInWorld("minecraft:overworld", false, 0);
        ticks(3);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            runtime.tick++;
            conductor.tick();
        }
    }

    private OperationStatus travel() {
        OperationResult result = conductor.requestTravel(GOAL);
        assertTrue(result.isAccepted(), result::toString);
        runtime.busy(FakePort.OWN);
        return result.status().orElseThrow();
    }

    @Test
    void acceptedTravelIsExecutingWithAuthorityAndOneEngineCall() {
        OperationStatus status = travel();

        assertEquals(OperationState.EXECUTING, status.state());
        assertTrue(status.contextValid());
        assertTrue(status.authorityHeld());
        assertEquals(List.of("route", "travel " + GOAL), port.calls);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state(), "in use");
    }

    @Test
    void withoutAReleasePolicyNothingIsEverGranted() {
        ContractService strict = new ContractService(runtime, contexts, sink);
        for (int i = 0; i < 50; i++) {
            strict.tick();
        }
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), strict.requestTravel(GOAL).rejection());
    }

    // --- cancellation requested vs confirmed ---

    @Test
    void cancelIsRequestedNotConfirmedWhileTheEngineStillSteers() {
        OperationId id = travel().id();
        ticks(2);

        OperationStatus after = conductor.cancel(id).status().orElseThrow();
        ticks(100); // engine keeps driving (live: up to 344 ticks)

        OperationStatus later = conductor.operationStatus(id).status().orElseThrow();
        assertEquals(OperationState.CANCELLING, after.state());
        assertEquals(OperationState.CANCELLING, later.state());
        assertTrue(later.authorityHeld(), "Cancelling still holds authority");
        assertNotEquals(OperationState.CANCELLED, later.state());
        assertEquals(1, port.calls.stream().filter("cancel"::equals).count());
    }

    @Test
    void repeatedCancelDoesNotReinvokeTheEngine() {
        OperationId id = travel().id();
        conductor.cancel(id);
        conductor.cancel(id);

        assertEquals(1, port.calls.stream().filter("cancel"::equals).count());
    }

    @Test
    void cancelThatThrowsIsUncertain() {
        OperationId id = travel().id();
        port.cancelFailure = new IllegalStateException("stayed active after being cancelled");

        assertEquals(OperationState.UNCERTAIN, conductor.cancel(id).status().orElseThrow().state());
    }

    // --- no new grant while unsafe; exclusivity ---

    @Test
    void conflictingRequestIsBlockedWithoutAnEngineCall() {
        conductor.cancel(travel().id());
        long before = port.engineCalls();

        assertEquals(Optional.of(RejectionReason.CONFLICTING_OPERATION), conductor.requestTravel(NavigationGoal.xz(1, 2)).rejection());
        assertEquals(before, port.engineCalls());
        assertTrue(sink.snapshot().stream().anyMatch(e -> e.type() == DiagnosticEvent.Type.INTERFERENCE && e.subject().equals("BLOCKED")));
    }

    @Test
    void declinedRequestLeavesTheResourceQuarantinedUntilEvidence() {
        port.next = new NavigationPort.Delegation(false, "BARITONE", true, "no IBaritone");

        assertEquals(Optional.of(RejectionReason.ENGINE_DECLINED), conductor.requestTravel(GOAL).rejection());
        port.next = new NavigationPort.Delegation(true, "BARITONE", true, "");
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(GOAL).rejection());
        ticks(3);
        assertTrue(conductor.requestTravel(GOAL).isAccepted());
    }

    @Test
    void unobservableRouteIsRejectedBeforeAnyEngineCall() {
        port.observableRoute = false;

        assertEquals(Optional.of(RejectionReason.BACKEND_UNOBSERVABLE), conductor.requestTravel(GOAL).rejection());
        assertEquals(0, port.engineCalls());
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
    }

    @Test
    void engineRoutingToAnUnobservableBackendIsUncertain() {
        port.next = new NavigationPort.Delegation(true, "TUNGSTEN", false, "");

        assertEquals(OperationState.UNCERTAIN, travel().state());
    }

    // --- context invalidation vs resource release; identity change vs disappearance ---

    @Test
    void contextChangeInvalidatesAuthorityCancelsBestEffortAndGrantsNothing() {
        OperationId id = travel().id();

        contexts.onInWorld("minecraft:the_nether", false, 1);

        OperationStatus status = conductor.operationStatus(id).status().orElseThrow();
        assertFalse(status.contextValid());
        assertFalse(status.authorityHeld());
        assertEquals(OperationState.CANCELLING, status.state(), "invalidation is not confirmation");
        assertEquals(1, port.calls.stream().filter("cancel"::equals).count());
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(GOAL).rejection(),
                "a new epoch does not prove the old operation stopped");
        assertEquals(Optional.of(RejectionReason.AUTHORITY_INVALIDATED), conductor.cancel(id).rejection());
    }

    @Test
    void sessionReplacementAloneGrantsNothing() {
        travel();
        contexts.onOutOfWorld(1);
        contexts.onInWorld("minecraft:overworld", false, 2);

        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(GOAL).rejection());
    }

    @Test
    void noTravelWithoutAContext() {
        contexts.onOutOfWorld(1);

        assertEquals(Optional.of(RejectionReason.NO_CURRENT_CONTEXT), conductor.requestTravel(GOAL).rejection());
        assertEquals(0, port.calls.size());
    }

    @Test
    void unknownOperationIsRejected() {
        travel();
        OperationId forged = new OperationId(99, contexts.current().orElseThrow());

        assertEquals(Optional.of(RejectionReason.UNKNOWN_OPERATION), conductor.cancel(forged).rejection());
        assertEquals(0, port.calls.stream().filter("cancel"::equals).count());
    }

    // --- off-thread ---

    @Test
    void offThreadCallsDoNotStartOrStopMovement() throws InterruptedException {
        OperationId id = travel().id();
        port.calls.clear();
        AtomicReference<OperationResult> travel = new AtomicReference<>();
        AtomicReference<OperationResult> cancel = new AtomicReference<>();
        Thread other = new Thread(() -> {
            travel.set(conductor.requestTravel(GOAL));
            cancel.set(conductor.cancel(id));
            conductor.tick();
        }, "worker");
        other.start();
        other.join();

        assertEquals(Optional.of(RejectionReason.OFF_THREAD), travel.get().rejection());
        assertEquals(Optional.of(RejectionReason.OFF_THREAD), cancel.get().rejection());
        assertEquals(List.of(), port.calls);
        assertEquals(OperationState.EXECUTING, conductor.operationStatus(id).status().orElseThrow().state());
    }

    // --- sink evidence ---

    @Test
    void sinkRecordsAuthorityAndStateTransitions() {
        OperationId id = travel().id();
        conductor.cancel(id);
        contexts.onInWorld("minecraft:the_end", false, 1);
        conductor.requestTravel(GOAL);

        List<DiagnosticEvent.Type> types = sink.snapshot().stream().map(DiagnosticEvent::type).toList();
        assertTrue(types.contains(DiagnosticEvent.Type.AUTHORITY_GRANTED));
        assertTrue(types.contains(DiagnosticEvent.Type.CANCEL_INVOKED));
        assertTrue(types.contains(DiagnosticEvent.Type.OPERATION_STATE_CHANGED));
        assertTrue(types.contains(DiagnosticEvent.Type.AUTHORITY_INVALIDATED));
        assertTrue(types.contains(DiagnosticEvent.Type.RESOURCE_STATE_CHANGED));
        assertTrue(sink.snapshot().stream().noneMatch(e -> e.detail().contains("-> CANCELLED")));
    }
}
