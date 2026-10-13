package conductor.engine.ostinato;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.movement.MovementBackends;
import conductor.contract.CapabilityAvailability;
import conductor.contract.CapabilityDescriptor;
import conductor.contract.CombatPort;
import conductor.contract.NavigationPort;
import conductor.contract.ObservationKind;
import conductor.contract.ObservationKinds;
import conductor.contract.Sample;
import conductor.runtime.EngineLayer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static conductor.contract.ObservationKinds.COMBAT_TARGET;
import static conductor.contract.ObservationKinds.CONTROLLING_PROCESS;
import static conductor.contract.ObservationKinds.FORCED_INPUTS;
import static conductor.contract.ObservationKinds.NEXT_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.PATH_CALCULATION_PENDING;
import static conductor.contract.ObservationKinds.PATH_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_BOUND;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_PATHING;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_STOPPING;
import static conductor.contract.ObservationKinds.TRAVEL_GOAL_ACTIVE;
import static conductor.contract.ObservationKinds.USE_KEY_HELD;

/**
 * Ostinato as Conductor's optional movement engine: travel (Stage 3-4), combat (Stage 5) and the engine signals
 * release evidence is built from. Conductor works without it; with it, the evidence rules of Stages 4-5 apply.
 */
public final class OstinatoLayer implements EngineLayer {

    static final String NAVIGATION = "navigation";
    static final String SECONDARY_BACKEND = "navigation.secondary-backend";
    static final String COMBAT = "combat";
    static final List<String> NAVIGATION_OPERATIONS = List.of("requestTravel", "cancel", "operationStatus");
    static final List<String> COMBAT_OPERATIONS = List.of("requestCombat", "cancel", "operationStatus");
    static final Set<ObservationKind<?>> SIGNALS = Set.of(PATH_SEGMENT_PRESENT, NEXT_SEGMENT_PRESENT,
            PATH_CALCULATION_PENDING, CONTROLLING_PROCESS, TRAVEL_GOAL_ACTIVE, FORCED_INPUTS, SECONDARY_BACKEND_BOUND,
            SECONDARY_BACKEND_PATHING, SECONDARY_BACKEND_STOPPING);

    private final OstinatoSignals signals;
    private final SecondaryBackendAccess secondary;
    private final NavigationPort navigation;
    private final CombatPort combat;
    private final CombatAccess combatAccess;

    OstinatoLayer(OstinatoSignals signals, SecondaryBackendAccess secondary, NavigationPort navigation,
                  CombatPort combat, CombatAccess combatAccess) {
        this.signals = signals;
        this.secondary = secondary;
        this.navigation = navigation;
        this.combat = combat;
        this.combatAccess = combatAccess;
    }

    /** The layer for the primary Ostinato instance. Game thread, in a world. */
    static OstinatoLayer forPrimary() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        SecondaryBackendAccess secondary = new TungstenBackendAccess();
        var engine = MovementBackends.engine(baritone);
        CombatAccess combat = new OstinatoCombatAccess(baritone);
        return new OstinatoLayer(new OstinatoSignals(baritone, secondary), secondary,
                new OstinatoNavigationPort(engine, MovementBackends::preferTungstenTravel),
                new OstinatoCombatPort(combat, engine), combat);
    }

    @Override
    public String name() {
        return "Ostinato";
    }

    @Override
    public List<CapabilityDescriptor> capabilities() {
        CapabilityDescriptor nav = new CapabilityDescriptor(NAVIGATION, CapabilityAvailability.AVAILABLE,
                "Ostinato IMovementEngine boundary; grants need release evidence (Stage 4)",
                List.of(PATH_SEGMENT_PRESENT, NEXT_SEGMENT_PRESENT, PATH_CALCULATION_PENDING, CONTROLLING_PROCESS,
                        TRAVEL_GOAL_ACTIVE, FORCED_INPUTS),
                navigation != null ? NAVIGATION_OPERATIONS : List.of());
        CapabilityDescriptor cmb = new CapabilityDescriptor(COMBAT,
                combat != null ? CapabilityAvailability.AVAILABLE : CapabilityAvailability.UNAVAILABLE,
                "Ostinato PvpProcess (players) and PveProcess (mobs); exclusive with travel; stop needs release "
                        + "evidence including the use key (Stage 5)",
                List.of(USE_KEY_HELD, COMBAT_TARGET), combat != null ? COMBAT_OPERATIONS : List.of());
        return List.of(nav, secondaryDescriptor(), cmb);
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
    @SuppressWarnings("unchecked")
    public <T> Sample<T> sample(ObservationKind<T> kind) {
        if (kind == COMBAT_TARGET) {
            return (Sample<T>) (combatAccess == null ? Sample.notObserved("combat not offered") : Sample.observed(combatAccess.target()));
        }
        return SIGNALS.contains(kind) ? signals.read(kind) : null;
    }

    @Override
    public Optional<NavigationPort> navigation() {
        return Optional.ofNullable(navigation);
    }

    @Override
    public Optional<CombatPort> combat() {
        return Optional.ofNullable(combat);
    }
}
