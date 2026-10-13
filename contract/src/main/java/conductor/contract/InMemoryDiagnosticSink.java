package conductor.contract;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, append-only diagnostic sink, safe from any thread. When it is full, the oldest events are dropped and
 * counted.
 * <p>
 * Thread safety comes only from {@link ConcurrentLinkedQueue} and the atomics. Recording never blocks, schedules,
 * or marshals work to another thread.
 */
public final class InMemoryDiagnosticSink implements DiagnosticSink {

    private final int capacity;
    private final ConcurrentLinkedQueue<DiagnosticEvent> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();

    public InMemoryDiagnosticSink(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    @Override
    public void record(DiagnosticEvent event) {
        events.add(Objects.requireNonNull(event, "event"));
        if (size.incrementAndGet() > capacity && events.poll() != null) {
            size.decrementAndGet();
            dropped.incrementAndGet();
        }
    }

    @Override
    public List<DiagnosticEvent> snapshot() {
        return List.copyOf(events);
    }

    public long droppedCount() {
        return dropped.get();
    }
}
