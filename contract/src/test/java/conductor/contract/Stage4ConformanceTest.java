package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 4 conformance tests: quarantine rules, evidence-based release, cancellation driven by evidence, quarantine
 * vs ownership-token validity, and interference classification.
 */
class Stage4ConformanceTest {

    private static final NavigationGoal GOAL = NavigationGoal.xz(40, 0);
    private static final int WINDOW = 5;

    private FakeRuntime runtime;
    private FakePort port;
    private InMemoryDiagnosticSink sink;
    private ContextTracker contexts;
    private ContractService conductor;

    @BeforeEach
    void setUp() {
        build(new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.of(200)));
    }

    private void build(ReleasePolicy policy) {
        runtime = new FakeRuntime();
        port = new FakePort();
        runtime.port = port;
        runtime.quiet();
        sink = new InMemoryDiagnosticSink(10_000);
        contexts = new ContextTracker(sink);
        conductor = new ContractService(runtime, contexts, sink, policy);
        contexts.onInWorld("minecraft:overworld", false, 0);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            runtime.tick++;
            conductor.tick();
        }
    }

    private OperationId travel() {
        OperationResult r = conductor.requestTravel(GOAL);
        assertTrue(r.isAccepted(), r::toString);
        runtime.busy(FakePort.OWN);
        return r.status().orElseThrow().id();
    }

    private OperationState state(OperationId id) {
        return conductor.operationStatus(id).status().orElseThrow().state();
    }

    private List<DiagnosticEvent> interference(String kind) {
        return sink.snapshot().stream()
                .filter(e -> e.type() == DiagnosticEvent.Type.INTERFERENCE && e.subject().equals(kind)).toList();
    }

    // --- evidence-based release ---

    @Test
    void resourceIsReleasedOnlyAfterAFullWindowOfQuietTicks() {
        ticks(WINDOW - 1);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
        ticks(1);
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
    }

    @Test
    void anyNonQuietTickRestartsTheWindow() {
        ticks(WINDOW - 1);
        runtime.samples.put(ObservationKinds.FORCED_INPUTS, Sample.observed(java.util.Set.of("JUMP")));
        ticks(1);
        runtime.quiet();
        ticks(WINDOW - 1);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
        ticks(1);
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
    }

    @Test
    void unknownReadingsAreNeverEvidence() {
        runtime.samples.put(ObservationKinds.FORCED_INPUTS, Sample.unknown("handler unavailable"));
        ticks(100);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
        runtime.samples.put(ObservationKinds.FORCED_INPUTS, Sample.notObserved("not read"));
        ticks(100);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
    }

    @Test
    void withoutAWindowTheResourceIsNeverReleased() {
        build(ReleasePolicy.none());
        ticks(1000);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
    }

    // --- cancellation driven by evidence ---

    @Test
    void cancelProgressesOnlyAsEvidenceArrives() {
        ticks(WINDOW);
        OperationId id = travel();
        ticks(10);
        conductor.cancel(id);
        ticks(50); // segment still running after cancel (live finding L1)
        assertEquals(OperationState.CANCELLING, state(id));

        // pathing stopped, but an input is still forced
        runtime.quiet();
        runtime.samples.put(ObservationKinds.FORCED_INPUTS, Sample.observed(java.util.Set.of("SNEAK")));
        ticks(1);
        assertEquals(OperationState.STOPPED_PATHING, state(id));
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state(), "pathing stopped is not override clear");

        runtime.quiet();
        ticks(WINDOW - 1);
        assertEquals(OperationState.STOPPED_PATHING, state(id));
        ticks(1);
        OperationStatus done = conductor.operationStatus(id).status().orElseThrow();
        assertEquals(OperationState.CANCELLED, done.state());
        assertFalse(done.authorityHeld(), "authority released with the resource");
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
        assertTrue(conductor.requestTravel(GOAL).isAccepted(), "reuse is permitted on evidence");
    }

    @Test
    void timeoutMakesTheOperationUncertainButNeverReleases() {
        build(new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.of(30)));
        ticks(WINDOW);
        OperationId id = travel();
        conductor.cancel(id);
        ticks(30);

        assertEquals(OperationState.UNCERTAIN, state(id));
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state(), "timeout != success");
        assertEquals(Optional.of(RejectionReason.CONFLICTING_OPERATION), conductor.requestTravel(GOAL).rejection());

        runtime.quiet();
        ticks(WINDOW);
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state(), "later evidence still releases");
        assertEquals(OperationState.UNCERTAIN, state(id), "the operation stays invalid for success");
    }

    @Test
    void engineGoingQuietWithoutCancelIsNotReportedAsArrival() {
        ticks(WINDOW);
        OperationId id = travel();
        ticks(20);
        runtime.quiet();
        ticks(WINDOW);

        assertEquals(OperationState.UNCERTAIN, state(id));
        assertTrue(conductor.operationStatus(id).status().orElseThrow().detail().contains("outcome not established"));
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
    }

    // --- quarantine vs ownership-token validity ---

    @Test
    void quarantineAndTokenValidityAreIndependent() {
        ticks(WINDOW);
        OperationId id = travel();
        contexts.onInWorld("minecraft:the_nether", false, runtime.tick);

        OperationStatus invalid = conductor.operationStatus(id).status().orElseThrow();
        assertFalse(invalid.contextValid());
        assertFalse(invalid.authorityHeld());
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state(), "token invalid, resource still unsafe");

        runtime.quiet();
        ticks(WINDOW);
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state(), "resource safe, token still invalid");
        assertEquals(Optional.of(RejectionReason.AUTHORITY_INVALIDATED), conductor.cancel(id).rejection());
        assertEquals(OperationState.CANCELLED, state(id));
    }

    @Test
    void contextChangeRestartsEvidenceEvenWhenTheResourceWasAvailable() {
        ticks(WINDOW);
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
        contexts.onInWorld("minecraft:the_end", false, runtime.tick);

        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(GOAL).rejection());
        ticks(WINDOW);
        assertTrue(conductor.requestTravel(GOAL).isAccepted());
    }

    // --- interference classification ---

    @Test
    void foreignProcessBeforeARequestIsDetectedAndBlocksTheGrant() {
        runtime.busy("engine.MineProcess");
        ticks(WINDOW * 3);

        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(GOAL).rejection());
        assertEquals(0, port.engineCalls());
        assertFalse(interference("DETECTED").isEmpty());
        assertTrue(interference("DETECTED").get(0).detail().startsWith("engine.MineProcess"));
    }

    @Test
    void foreignProcessDuringAnOperationMakesItUncertain() {
        ticks(WINDOW);
        OperationId id = travel();
        ticks(5);
        assertTrue(interference("DETECTED").isEmpty(), "own controller is not interference");

        runtime.busy("engine.AirProcess");
        ticks(1);
        assertEquals(OperationState.UNCERTAIN, state(id));
        assertEquals(1, interference("DETECTED").size());
    }

    @Test
    void activityAfterReleaseQuarantinesAgainAndIsDetected() {
        ticks(WINDOW);
        OperationId id = travel();
        conductor.cancel(id);
        runtime.quiet();
        ticks(WINDOW);
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());

        runtime.busy("engine.AirProcess"); // live: drowning protection took over ~200 ticks after release
        ticks(1);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
        assertTrue(interference("DETECTED").stream().anyMatch(e -> e.detail().startsWith("engine.AirProcess")));
        assertNotEquals(OperationState.UNCERTAIN, state(id), "a finished operation is not blamed for later activity");
    }

    @Test
    void boundSecondaryBackendBlocksRelease() {
        FakeRuntime withSecondary = new FakeRuntime() {
            @Override
            public List<CapabilityDescriptor> capabilities() {
                return List.of(NAVIGATION, new CapabilityDescriptor("secondary", CapabilityAvailability.AVAILABLE, "",
                        List.of(ObservationKinds.SECONDARY_BACKEND_BOUND), List.of()));
            }
        };
        withSecondary.quiet();
        withSecondary.samples.put(ObservationKinds.SECONDARY_BACKEND_BOUND, Sample.observed(true));
        ContextTracker ctx = new ContextTracker(sink);
        ContractService service = new ContractService(withSecondary, ctx, sink, new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.empty()));
        ctx.onInWorld("minecraft:overworld", false, 0);
        for (int i = 0; i < 100; i++) {
            service.tick();
        }
        assertEquals(ResourceState.QUARANTINED, service.resourceStatus().state());

        withSecondary.samples.put(ObservationKinds.SECONDARY_BACKEND_BOUND, Sample.observed(false));
        for (int i = 0; i < WINDOW; i++) {
            service.tick();
        }
        assertEquals(ResourceState.AVAILABLE, service.resourceStatus().state());
    }
}
