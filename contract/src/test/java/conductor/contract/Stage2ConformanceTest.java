package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import static conductor.contract.ObservationKinds.CONTROLLING_PROCESS;
import static conductor.contract.ObservationKinds.PATH_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_PATHING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 2 conformance tests from the specification: observation availability vs freshness, rejection of old-context
 * results, fail-closed freshness, missing data never becoming a negative, off-thread rejection, and sink evidence.
 */
class Stage2ConformanceTest {

    private static final FreshnessPolicy FIVE_TICKS = FreshnessPolicy.maxAge(OptionalLong.of(5), Optional.empty());

    private FakeRuntime runtime;
    private InMemoryDiagnosticSink sink;
    private ContextTracker contexts;
    private Conductor conductor;

    @BeforeEach
    void setUp() {
        runtime = new FakeRuntime();
        sink = new InMemoryDiagnosticSink(1000);
        contexts = new ContextTracker(sink);
        conductor = new ContractService(runtime, contexts, sink);
        contexts.onInWorld("minecraft:overworld", false, runtime.tick);
        runtime.samples.put(PATH_SEGMENT_PRESENT, Sample.observed(false));
    }

    // --- availability vs freshness ---

    @Test
    void observedButStaleIsRejectedWithAvailabilityIntact() {
        Observation<Boolean> obs = conductor.observe(PATH_SEGMENT_PRESENT, FIVE_TICKS).observation().orElseThrow();
        runtime.advance(6, 0);

        ObservationResult<Boolean> result = conductor.revalidate(obs, FIVE_TICKS);

        assertEquals(Optional.of(RejectionReason.STALE), result.rejection());
        assertEquals(Optional.of(Freshness.STALE), result.freshness());
        assertEquals(Availability.OBSERVED, result.observation().orElseThrow().availability());
        assertEquals(Optional.of(false), result.observation().orElseThrow().observedValue());
    }

    @Test
    void notObservedButFreshIsAcceptedWithoutAValue() {
        runtime.samples.put(PATH_SEGMENT_PRESENT, Sample.notObserved("nothing read"));

        ObservationResult<Boolean> result = conductor.observe(PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick());

        assertTrue(result.isAccepted());
        assertEquals(Optional.of(Freshness.FRESH), result.freshness());
        assertEquals(Availability.NOT_OBSERVED, result.observation().orElseThrow().availability());
        assertTrue(result.observation().orElseThrow().observedValue().isEmpty());
    }

    @Test
    void confirmedNegativeIsObserved() {
        ObservationResult<Boolean> result = conductor.observe(PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick());

        assertEquals(Availability.OBSERVED, result.observation().orElseThrow().availability());
        assertEquals(Optional.of(false), result.observation().orElseThrow().observedValue());
    }

    @Test
    void durationBoundIsEnforcedIndependentlyOfTicks() {
        FreshnessPolicy policy = FreshnessPolicy.maxAge(OptionalLong.of(100), Optional.of(Duration.ofMillis(50)));
        Observation<Boolean> obs = conductor.observe(PATH_SEGMENT_PRESENT, policy).observation().orElseThrow();
        runtime.advance(1, Duration.ofMillis(51).toNanos());

        assertEquals(Optional.of(RejectionReason.STALE), conductor.revalidate(obs, policy).rejection());
    }

    @Test
    void sameContextWithinAgeRevalidates() {
        Observation<Boolean> obs = conductor.observe(PATH_SEGMENT_PRESENT, FIVE_TICKS).observation().orElseThrow();
        runtime.advance(5, 0);

        assertTrue(conductor.revalidate(obs, FIVE_TICKS).isAccepted());
    }

    // --- old-context results rejected ---

    @Test
    void dimensionChangeInvalidatesEarlierObservations() {
        Observation<Boolean> obs = conductor.observe(PATH_SEGMENT_PRESENT, FIVE_TICKS).observation().orElseThrow();
        contexts.onInWorld("minecraft:the_nether", false, runtime.tick);

        assertEquals(Optional.of(RejectionReason.CONTEXT_INVALIDATED), conductor.revalidate(obs, FIVE_TICKS).rejection());
    }

    @Test
    void worldReplacementInvalidatesEvenInTheSameDimensionAndTick() {
        Observation<Boolean> obs = conductor.observe(PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick()).observation().orElseThrow();
        contexts.onWorldReplaced("minecraft:overworld", runtime.tick);

        ObservationResult<Boolean> result = conductor.revalidate(obs, FreshnessPolicy.sameTick());
        assertEquals(Optional.of(RejectionReason.CONTEXT_INVALIDATED), result.rejection());
        assertTrue(result.freshness().isEmpty(), "freshness is not evaluated for an invalid context");
    }

    @Test
    void sessionReplacementInvalidatesEarlierObservations() {
        Observation<Boolean> obs = conductor.observe(PATH_SEGMENT_PRESENT, FIVE_TICKS).observation().orElseThrow();
        contexts.onOutOfWorld(runtime.tick);
        contexts.onInWorld("minecraft:overworld", false, runtime.tick);

        assertEquals(Optional.of(RejectionReason.CONTEXT_INVALIDATED), conductor.revalidate(obs, FIVE_TICKS).rejection());
        assertTrue(obs.context().sessionId() < contexts.current().orElseThrow().sessionId());
    }

    @Test
    void connectionChangeInvalidatesEarlierObservations() {
        Observation<Boolean> obs = conductor.observe(PATH_SEGMENT_PRESENT, FIVE_TICKS).observation().orElseThrow();
        contexts.onInWorld("minecraft:overworld", true, runtime.tick);

        assertEquals(Optional.of(RejectionReason.CONTEXT_INVALIDATED), conductor.revalidate(obs, FIVE_TICKS).rejection());
    }

    @Test
    void observationsAreRejectedWhileNoContextExists() {
        Observation<Boolean> obs = conductor.observe(PATH_SEGMENT_PRESENT, FIVE_TICKS).observation().orElseThrow();
        contexts.onOutOfWorld(runtime.tick);
        int before = runtime.sampleCalls.get();

        assertEquals(Optional.of(RejectionReason.CONTEXT_INVALIDATED), conductor.revalidate(obs, FIVE_TICKS).rejection());
        assertEquals(Optional.of(RejectionReason.NO_CURRENT_CONTEXT), conductor.observe(PATH_SEGMENT_PRESENT, FIVE_TICKS).rejection());
        assertEquals(before, runtime.sampleCalls.get(), "nothing is sampled without a context");
    }

    // --- fail closed on freshness ---

    @Test
    void absentFreshnessPolicyRejectsWithoutSampling() {
        ObservationResult<Boolean> result = conductor.observe(PATH_SEGMENT_PRESENT, null);

        assertEquals(Optional.of(RejectionReason.NO_FRESHNESS_POLICY), result.rejection());
        assertEquals(0, runtime.sampleCalls.get());
    }

    @Test
    void absentFreshnessPolicyRejectsRevalidation() {
        Observation<Boolean> obs = conductor.observe(PATH_SEGMENT_PRESENT, FIVE_TICKS).observation().orElseThrow();

        assertEquals(Optional.of(RejectionReason.NO_FRESHNESS_POLICY), conductor.revalidate(obs, null).rejection());
    }

    // --- missing data never becomes a negative ---

    @Test
    void samplingFailureBecomesUnknownAndIsRecorded() {
        runtime.failure = new IllegalStateException("boom");

        ObservationResult<Boolean> result = conductor.observe(PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick());

        assertEquals(Availability.UNKNOWN, result.observation().orElseThrow().availability());
        assertTrue(result.observation().orElseThrow().observedValue().isEmpty());
        assertTrue(has(DiagnosticEvent.Type.PROVIDER_FAULT));
    }

    @Test
    void missingSampleBecomesUnknown() {
        runtime.samples.clear();

        ObservationResult<Optional<String>> result = conductor.observe(CONTROLLING_PROCESS, FreshnessPolicy.sameTick());

        assertEquals(Availability.UNKNOWN, result.observation().orElseThrow().availability());
        assertTrue(result.observation().orElseThrow().observedValue().isEmpty());
    }

    @Test
    void kindTheRuntimeDoesNotProvideIsRejectedWithoutSampling() {
        ObservationResult<Boolean> result = conductor.observe(SECONDARY_BACKEND_PATHING, FreshnessPolicy.sameTick());

        assertEquals(Optional.of(RejectionReason.UNSUPPORTED_KIND), result.rejection());
        assertEquals(0, runtime.sampleCalls.get());
    }

    // --- off-thread calls ---

    @Test
    void offThreadCallsReadNothingAndAreRecorded() throws InterruptedException {
        int readsBefore = runtime.stateReads.get();
        AtomicReference<ObservationResult<Boolean>> observed = new AtomicReference<>();
        AtomicReference<DiscoveryResult> discovered = new AtomicReference<>();
        Thread other = new Thread(() -> {
            observed.set(conductor.observe(PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick()));
            discovered.set(conductor.discover());
        }, "not-the-game-thread");
        other.start();
        other.join();

        assertEquals(Optional.of(RejectionReason.OFF_THREAD), observed.get().rejection());
        assertEquals(RejectionReason.OFF_THREAD, discovered.get().rejection());
        assertEquals(0, runtime.sampleCalls.get());
        assertEquals(readsBefore, runtime.stateReads.get(), "no tick, clock or capability read off-thread");
        List<DiagnosticEvent> violations = sink.snapshot().stream()
                .filter(e -> e.type() == DiagnosticEvent.Type.OFF_THREAD_VIOLATION).toList();
        assertEquals(2, violations.size());
        violations.forEach(v -> {
            assertEquals("not-the-game-thread", v.thread());
            assertEquals(-1, v.tick());
            assertEquals(null, v.context());
        });
    }

    // --- discovery ---

    @Test
    void discoveryExposesNoOperationsInStage2() {
        DiscoveryResult result = conductor.discover();

        assertTrue(result.isAccepted());
        assertEquals(contexts.current(), result.context());
        assertFalse(result.capabilities().isEmpty());
        result.capabilities().forEach(c -> assertTrue(c.operations().isEmpty(), c.id()));
    }

    // --- sink evidence ---

    @Test
    void sinkRecordsContextTransitionsAndRejectionReasons() {
        contexts.onInWorld("minecraft:the_end", false, 1);
        contexts.onOutOfWorld(2);
        conductor.observe(PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick());

        assertTrue(has(DiagnosticEvent.Type.CONTEXT_ESTABLISHED));
        assertTrue(has(DiagnosticEvent.Type.CONTEXT_INVALIDATED));
        assertTrue(has(DiagnosticEvent.Type.CONTEXT_ENDED));
        assertTrue(sink.snapshot().stream().anyMatch(e -> e.type() == DiagnosticEvent.Type.OBSERVATION_REJECTED
                && e.detail().startsWith("NO_CURRENT_CONTEXT")));
    }

    private boolean has(DiagnosticEvent.Type type) {
        return sink.snapshot().stream().anyMatch(e -> e.type() == type);
    }
}
