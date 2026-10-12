package conductor.runtime.ostinato;

import conductor.contract.BlockPoint;
import conductor.contract.CapabilityAvailability;
import conductor.contract.CapabilityDescriptor;
import conductor.contract.CombatPort;
import conductor.contract.ContentId;
import conductor.contract.ContentResult;
import conductor.contract.NavigationPort;
import conductor.contract.ObservationKind;
import conductor.contract.RuntimeIntegration;
import conductor.contract.Sample;

import java.util.List;
import java.util.Optional;

import static conductor.contract.ObservationKinds.COMBAT_TARGET;
import static conductor.contract.ObservationKinds.CONTROLLING_PROCESS;
import static conductor.contract.ObservationKinds.FORCED_INPUTS;
import static conductor.contract.ObservationKinds.NEXT_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.PATH_CALCULATION_PENDING;
import static conductor.contract.ObservationKinds.PATH_SEGMENT_PRESENT;
import static conductor.contract.ObservationKinds.PLAYER_POSITION;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_BOUND;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_PATHING;
import static conductor.contract.ObservationKinds.SECONDARY_BACKEND_STOPPING;
import static conductor.contract.ObservationKinds.TRAVEL_GOAL_ACTIVE;
import static conductor.contract.ObservationKinds.USE_KEY_HELD;

/**
 * Runtime integration for Ostinato {@code main} (Minecraft 1.21.11). Observations are read-only. Navigation goes
 * through {@link OstinatoNavigationPort} only.
 */
final class OstinatoRuntimeIntegration implements RuntimeIntegration {

    static final String NAVIGATION = "navigation";
    static final String SECONDARY_BACKEND = "navigation.secondary-backend";
    static final String PLAYER = "player";
    static final String CONTENT = "content";
    static final String COMBAT = "combat";
    static final List<String> COMBAT_OPERATIONS = List.of("requestCombat", "cancel", "operationStatus");

    private final GameAccess game;
    private final LifecycleListener lifecycle;
    private final OstinatoSignals signals;
    private final SecondaryBackendAccess secondary;
    private final NavigationPort navigation;
    private final EngineFeatures engineFeatures;
    private CombatPort combatPort;
    private CombatAccess combatAccess;

    OstinatoRuntimeIntegration(GameAccess game, LifecycleListener lifecycle, OstinatoSignals signals,
                               SecondaryBackendAccess secondary, NavigationPort navigation) {
        this(game, lifecycle, signals, secondary, navigation,
                EngineFeatures.ofLoader(baritone.api.IBaritone.class.getClassLoader()));
    }

    OstinatoRuntimeIntegration(GameAccess game, LifecycleListener lifecycle, OstinatoSignals signals,
                               SecondaryBackendAccess secondary, NavigationPort navigation,
                               EngineFeatures engineFeatures) {
        this.engineFeatures = engineFeatures;
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
                "Ostinato IMovementEngine boundary; single consumer; grants need release evidence (Stage 4)",
                List.of(PATH_SEGMENT_PRESENT, NEXT_SEGMENT_PRESENT, PATH_CALCULATION_PENDING, CONTROLLING_PROCESS,
                        TRAVEL_GOAL_ACTIVE, FORCED_INPUTS),
                navigation != null ? NAVIGATION_OPERATIONS : List.of());
        CapabilityDescriptor player = new CapabilityDescriptor(PLAYER, CapabilityAvailability.AVAILABLE,
                "read-only player state", List.of(PLAYER_POSITION), List.of());
        CapabilityDescriptor combat = new CapabilityDescriptor(COMBAT,
                combatPort != null ? CapabilityAvailability.AVAILABLE : CapabilityAvailability.UNAVAILABLE,
                "Ostinato PvpProcess (players) and PveProcess (mobs); exclusive with travel; stop needs release "
                        + "evidence including the use key (Stage 5)",
                List.of(USE_KEY_HELD, COMBAT_TARGET), combatPort != null ? COMBAT_OPERATIONS : List.of());
        return List.of(primary, secondaryDescriptor(), player, contentDescriptor(), combat);
    }

    /** Offer combat delegation (Stage 5). Called once by the installer. */
    void enableCombat(CombatPort port, CombatAccess access) {
        this.combatPort = port;
        this.combatAccess = access;
    }

    @Override
    public Optional<CombatPort> combat() {
        return Optional.ofNullable(combatPort);
    }

    private CapabilityDescriptor contentDescriptor() {
        String detail;
        try {
            detail = "item/block/entity/effect ids via built-in registries; engine features present: "
                    + engineFeatures.present();
        } catch (RuntimeException | LinkageError e) {
            detail = "item/block/entity/effect ids via built-in registries; engine feature probe failed: " + e;
        }
        return new CapabilityDescriptor(CONTENT, CapabilityAvailability.AVAILABLE, detail, List.of(), List.of("content"));
    }

    @Override
    public ContentResult content(ContentId content) {
        if (content.kind() == ContentId.Kind.ENGINE) {
            return engineFeatures.check(content);
        }
        ContentResult.Presence presence = game.registryPresence(content.kind(), content.id());
        if (presence == null) {
            return new ContentResult(content, ContentResult.Presence.UNKNOWN, "no built-in registry answer for this id");
        }
        return new ContentResult(content, presence, "built-in registry");
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
        if (kind == PLAYER_POSITION) {
            BlockPoint at = game.playerBlock();
            return (Sample<T>) (at == null ? Sample.notObserved("no player") : Sample.observed(at));
        }
        if (kind == USE_KEY_HELD) {
            Boolean down = game.useKeyDown();
            return (Sample<T>) (down == null ? Sample.notObserved("no game options yet") : Sample.observed(down));
        }
        if (kind == COMBAT_TARGET) {
            if (combatAccess == null) {
                return (Sample<T>) Sample.notObserved("combat not offered");
            }
            return (Sample<T>) Sample.observed(combatAccess.target());
        }
        return signals.read(kind);
    }

    @Override
    public Optional<NavigationPort> navigation() {
        return Optional.ofNullable(navigation);
    }
}
