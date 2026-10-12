package conductor.runtime.ostinato;

import conductor.contract.CapabilityAvailability;
import conductor.contract.CapabilityDescriptor;
import conductor.contract.NavigationPort;
import conductor.contract.ObservationKind;
import conductor.contract.RuntimeIntegration;
import conductor.contract.Sample;

import java.util.List;
import java.util.Optional;

import static conductor.contract.ObservationKinds.CONTROLLING_PROCESS;
import static conductor.contract.ObservationKinds.FORCED_INPUTS;
import static conductor.contract.ObservationKinds.NEXT_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.PATH_CALCULATION_PENDING;
import static conductor.contract.ObservationKinds.PATH_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_BOUND;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_PATHING;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_STOPPING;
import static conductor.contract.ObservationKinds.TRAVEL_GOAL_ACTIVE;

/**
 * Runtime integration for Ostinato {@code main} (Minecraft 1.21.11). Observations are read-only. Navigation goes
 * through {@link OstinatoNavigationPort} only.
 */
final class OstinatoRuntimeIntegration implements RuntimeIntegration {

    static final String NAVIGATION = "navigation";
    static final String SECONDARY_BACKEND = "navigation.secondary-backend";

    private final GameAccess game;
    private final LifecycleListener lifecycle;
    private final OstinatoSignals signals;
    private final SecondaryBackendAccess secondary;
    private final NavigationPort navigation;

    OstinatoRuntimeIntegration(GameAccess game, LifecycleListener lifecycle, OstinatoSignals signals,
                               SecondaryBackendAccess secondary, NavigationPort navigation) {
        this.game = game;
        this.lifecycle = lifecycle;
        this.signals = signals;
        this.secondary = secondary;
        this.navigation = navigation;
    }

    static final List<String> NAVIGATION_OPERATIONS = List.of("requestTravel", "cancel", "operationStatus");

    @Override
    public boolean isGameThread() {
        return game.isGameThread();
    }

    @Override
    public long currentTick() {
        return lifecycle.lastTick();
    }

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public List<CapabilityDescriptor> capabilities() {
        CapabilityDescriptor primary = new CapabilityDescriptor(NAVIGATION, CapabilityAvailability.AVAILABLE,
                "Ostinato IMovementEngine boundary; single consumer, one grant per installation (Stage 3)",
                List.of(PATH_SEGMENT_PRESENT, NEXT_SEGMENT_PRESENT, PATH_CALCULATION_PENDING, CONTROLLING_PROCESS,
                        TRAVEL_GOAL_ACTIVE, FORCED_INPUTS),
                navigation != null ? NAVIGATION_OPERATIONS : List.of());
        return List.of(primary, secondaryDescriptor());
    }

    private CapabilityDescriptor secondaryDescriptor() {
        CapabilityAvailability availability;
        String detail;
        try {
            boolean bound = secondary.isBound();
            availability = bound ? CapabilityAvailability.AVAILABLE : CapabilityAvailability.UNAVAILABLE;
            detail = bound ? "Tungsten bound" : "Tungsten not bound";
        } catch (RuntimeException | LinkageError e) {
            availability = CapabilityAvailability.UNKNOWN;
            detail = "probe failed: " + e;
        }
        return new CapabilityDescriptor(SECONDARY_BACKEND, availability, detail,
                List.of(SECONDARY_BACKEND_BOUND, SECONDARY_BACKEND_PATHING, SECONDARY_BACKEND_STOPPING), List.of());
    }

    @Override
    public <T> Sample<T> sample(ObservationKind<T> kind) {
        return signals.read(kind);
    }

    @Override
    public Optional<NavigationPort> navigation() {
        return Optional.ofNullable(navigation);
    }
}
