package conductor.contract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Test runtime. Counts every read so tests can prove what a rejected call did not touch. */
class FakeRuntime implements RuntimeIntegration {

    volatile Thread gameThread = Thread.currentThread();
    long tick;
    long nanos;
    final Map<ObservationKind<?>, Object> samples = new HashMap<>();
    RuntimeException failure;
    NavigationPort port;
    CombatPort combatPort;
    WorldPort worldPort;
    ActionPort actionPort;
    /** Whether the runtime lists USE_KEY_HELD among its observations. */
    boolean reportsUseKey;
    final Map<ContentId, ContentResult.Presence> content = new HashMap<>();
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
        if (!reportsUseKey) {
            return List.of(NAVIGATION);
        }
        return List.of(NAVIGATION, new CapabilityDescriptor("combat", CapabilityAvailability.AVAILABLE, "fake",
                List.of(ObservationKinds.USE_KEY_HELD, ObservationKinds.COMBAT_TARGET), List.of()));
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
    public ContentResult content(ContentId id) {
        ContentResult.Presence p = content.get(id);
        return p == null
                ? new ContentResult(id, ContentResult.Presence.UNKNOWN, "fake: not configured")
                : new ContentResult(id, p, "fake");
    }

    @Override
    public Optional<WorldPort> world() {
        return Optional.ofNullable(worldPort);
    }

    @Override
    public Optional<ActionPort> actions() {
        return Optional.ofNullable(actionPort);
    }

    @Override
    public Optional<CombatPort> combat() {
        return Optional.ofNullable(combatPort);
    }

    @Override
    public Optional<NavigationPort> navigation() {
        return Optional.ofNullable(port);
    }

    /** Every navigation signal reads as quiet. */
    void quiet() {
        samples.put(ObservationKinds.PATH_SEGMENT_PRESENT, Sample.observed(false));
        samples.put(ObservationKinds.NEXT_SEGMENT_PRESENT, Sample.observed(false));
        samples.put(ObservationKinds.PATH_CALCULATION_PENDING, Sample.observed(false));
        samples.put(ObservationKinds.CONTROLLING_PROCESS, Sample.observed(java.util.Optional.<String>empty()));
        samples.put(ObservationKinds.TRAVEL_GOAL_ACTIVE, Sample.observed(false));
        samples.put(ObservationKinds.FORCED_INPUTS, Sample.observed(java.util.Set.<String>of()));
        samples.put(ObservationKinds.USE_KEY_HELD, Sample.observed(false));
    }

    /** The engine is executing a segment for {@code controller}. */
    void busy(String controller) {
        quiet();
        samples.put(ObservationKinds.PATH_SEGMENT_PRESENT, Sample.observed(true));
        samples.put(ObservationKinds.CONTROLLING_PROCESS, Sample.observed(java.util.Optional.of(controller)));
        samples.put(ObservationKinds.FORCED_INPUTS, Sample.observed(java.util.Set.of("MOVE_FORWARD")));
    }

    void advance(long ticks, long nanosDelta) {
        tick += ticks;
        nanos += nanosDelta;
    }
}
