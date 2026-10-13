package conductor.contract;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractTypesTest {

    @Test
    void freshnessPolicyHasNoImplicitBounds() {
        assertThrows(IllegalArgumentException.class, () -> FreshnessPolicy.maxAge(OptionalLong.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new FreshnessPolicy(FreshnessPolicy.Requirement.SAME_TICK, OptionalLong.of(1), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> FreshnessPolicy.maxAge(OptionalLong.of(-1), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> FreshnessPolicy.maxAge(OptionalLong.empty(), Optional.of(Duration.ofMillis(-1))));
    }

    @Test
    void backwardsClockIsStale() {
        FreshnessPolicy policy = FreshnessPolicy.maxAge(OptionalLong.of(10), Optional.empty());
        assertEquals(Freshness.STALE, policy.evaluate(5, 0, 4, 0));
        assertEquals(Freshness.STALE, FreshnessPolicy.sameTick().evaluate(5, 10, 5, 9));
    }

    @Test
    void sampleValueOnlyWhenObserved() {
        assertThrows(IllegalArgumentException.class, () -> new Sample<>(Availability.UNKNOWN, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new Sample<>(Availability.OBSERVED, null, ""));
    }

    @Test
    void epochsAreNeverReusedAcrossSessions() {
        ContextTracker tracker = new ContextTracker(new InMemoryDiagnosticSink(10));
        List<ContextId> seen = new ArrayList<>();
        tracker.onInWorld("a", false, 0);
        seen.add(tracker.current().orElseThrow());
        tracker.onInWorld("b", false, 1);
        seen.add(tracker.current().orElseThrow());
        tracker.onOutOfWorld(2);
        tracker.onInWorld("a", false, 3);
        seen.add(tracker.current().orElseThrow());

        assertEquals(3, seen.stream().mapToLong(ContextId::epoch).distinct().count());
        assertEquals(seen.get(0).sessionId(), seen.get(1).sessionId());
        assertNotEquals(seen.get(1).sessionId(), seen.get(2).sessionId());
    }

    @Test
    void sinkIsBoundedAndSafeFromManyThreads() throws InterruptedException {
        InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(100);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    sink.record(DiagnosticEvent.offThread("x", "y"));
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        int kept = sink.snapshot().size();
        assertTrue(kept <= 100, "kept " + kept);
        assertEquals(8000, kept + sink.droppedCount());
    }
}
