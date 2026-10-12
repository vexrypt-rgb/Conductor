package conductor.contract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Test runtime. Counts every read so tests can prove what a rejected call did not touch. */
final class FakeRuntime implements RuntimeIntegration {

    volatile Thread gameThread = Thread.currentThread();
    long tick;
    long nanos;
    final Map<ObservationKind<?>, Object> samples = new HashMap<>();
    RuntimeException failure;
    NavigationPort port;
    final AtomicInteger sampleCalls = new AtomicInteger();
    final AtomicInteger stateReads = new AtomicInteger();

    static final CapabilityDescriptor NAVIGATION = new CapabilityDescriptor(
            "navigation", CapabilityAvailability.AVAILABLE, "fake", ObservationKinds.ALL.subList(0, 6), List.of());

    @Override
    public boolean isGameThread() {
        return Thread.currentThread() == gameThread;
    }

    @Override
    public long currentTick() {
        stateReads.incrementAndGet();
        return tick;
    }

    @Override
    public long nanoTime() {
        stateReads.incrementAndGet();
        return nanos;
    }

    @Override
    public List<CapabilityDescriptor> capabilities() {
        stateReads.incrementAndGet();
        return List.of(NAVIGATION);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Sample<T> sample(ObservationKind<T> kind) {
        sampleCalls.incrementAndGet();
        if (failure != null) {
            throw failure;
        }
        return (Sample<T>) samples.get(kind);
    }

    @Override
    public Optional<NavigationPort> navigation() {
        return Optional.ofNullable(port);
    }

    void advance(long ticks, long nanosDelta) {
        tick += ticks;
        nanos += nanosDelta;
    }
}
