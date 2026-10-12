package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 3 conformance tests from the specification: cancellation requested vs confirmed, context invalidation vs
 * resource release, identity change vs disappearance, no new grant while the resource is unsafe or uncertain,
 * off-thread calls not starting movement, contract-level exclusivity, and sink evidence for authority transitions.
 */
class Stage3ConformanceTest {

    private static final NavigationGoal GOAL = NavigationGoal.getToBlock(10, 64, -5);

    private FakeRuntime runtime;
    private FakePort port;
    private InMemoryDiagnosticSink sink;
    private ContextTracker contexts;
    private Conductor conductor;

    @BeforeEach
    void setUp() {
        runtime = new FakeRuntime();
        port = new FakePort();
        runtime.port = port;
        sink = new InMemoryDiagnosticSink(1000);
        contexts = new ContextTracker(sink);
        conductor = new ContractService(runtime, contexts, sink);
        contexts.onInWorld("minecraft:overworld", false, 0);
    }

    private OperationStatus travel() {
        OperationResult result = conductor.requestTravel(GOAL);
        assertTrue(result.isAccepted(), result::toString);
        return result.status().orElseThrow();
    }

    @Test
    void acceptedTravelIsExecutingWithAuthorityAndOneEngineCall() {
        OperationStatus status = travel();

        assertEquals(OperationState.EXECUTING, status.state());
        assertTrue(status.contextValid());
        assertTrue(status.authorityHeld());
        assertEquals(List.of("route", "travel " + GOAL), port.calls);
    }

    // --- cancellation requested vs confirmed ---

    @Test
    void cancelIsRequestedNeverConfirmed() {
        OperationId id = travel().id();

        OperationStatus after = conductor.cancel(id).status().orElseThrow();
        OperationStatus later = conductor.operationStatus(id).status().orElseThrow();

        assertEquals(OperationState.CANCELLING, after.state());
        assertEquals(OperationState.CANCELLING, later.state());
        assertTrue(after.authorityHeld(), "Cancelling still holds authority");
        assertEquals(1, port.calls.stream().filter("cancel"::equals).count());
        assertNotEquals(OperationState.CANCELLED, later.state());
        assertNotEquals(OperationState.STOPPED_PATHING, later.state());
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

    // --- no new grant while unsafe or uncertain ---

    @Test
    void noSecondGrantAfterCancelRequested() {
        conductor.cancel(travel().id());

        OperationResult second = conductor.requestTravel(GOAL);
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), second.rejection());
        assertEquals(1, port.calls.stream().filter(c -> c.startsWith("travel")).count());
    }

    @Test
    void exclusivityRejectsAConflictingRequestWithoutAnEngineCall() {
        travel();
        long before = port.engineCalls();

        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(NavigationGoal.xz(1, 2)).rejection());
        assertEquals(before, port.engineCalls());
    }

    @Test
    void declinedRequestSpendsTheGrant() {
        port.next = new NavigationPort.Delegation(false, "BARITONE", true, "no IBaritone");

        assertEquals(Optional.of(RejectionReason.ENGINE_DECLINED), conductor.requestTravel(GOAL).rejection());
        port.next = new NavigationPort.Delegation(true, "BARITONE", true, "");
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(GOAL).rejection());
    }

    @Test
    void unobservableRouteIsRejectedBeforeAnyEngineCallAndKeepsTheGrant() {
        port.observableRoute = false;

        assertEquals(Optional.of(RejectionReason.BACKEND_UNOBSERVABLE), conductor.requestTravel(GOAL).rejection());
        assertEquals(0, port.engineCalls());

        port.observableRoute = true;
        assertTrue(conductor.requestTravel(GOAL).isAccepted(), "no engine call was made, so the grant was not spent");
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
    void sessionReplacementGrantsNothingEither() {
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
        assertTrue(sink.snapshot().stream().anyMatch(e -> e.type() == DiagnosticEvent.Type.OPERATION_REJECTED
                && e.detail().startsWith("RESOURCE_NOT_RELEASED")));
        assertTrue(sink.snapshot().stream().noneMatch(e -> e.detail().contains("-> CANCELLED")));
    }
}
