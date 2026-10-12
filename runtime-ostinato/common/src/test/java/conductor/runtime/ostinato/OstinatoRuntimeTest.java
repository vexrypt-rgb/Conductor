package conductor.runtime.ostinato;

import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.input.Input;
import conductor.contract.Availability;
import conductor.contract.CapabilityAvailability;
import conductor.contract.CapabilityDescriptor;
import conductor.contract.Conductor;
import conductor.contract.ContentId;
import conductor.contract.ContentResult;
import conductor.contract.ContextId;
import conductor.contract.ContextTracker;
import conductor.contract.ContractService;
import conductor.contract.DiagnosticEvent;
import conductor.contract.DiscoveryResult;
import conductor.contract.FreshnessPolicy;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.Observation;
import conductor.contract.ObservationKinds;
import conductor.contract.ObservationResult;
import conductor.contract.RejectionReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Stage 2 tests for the Ostinato runtime integration. The engine is replaced by Mockito mocks of Ostinato's API
 * interfaces, so any call other than the expected reads fails the test. Nothing is run against a live game.
 */
class OstinatoRuntimeTest {

    private final FakeGameAccess game = new FakeGameAccess();
    private final FakeSecondaryBackend secondary = new FakeSecondaryBackend();
    private final InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(1000);

    private IBaritone baritone;
    private IPathingBehavior pathing;
    private IPathingControlManager control;
    private ICustomGoalProcess customGoal;
    private IInputOverrideHandler inputs;

    private ContextTracker contexts;
    private LifecycleListener lifecycle;
    private Conductor conductor;
    private int tickCount;

    /** Ostinato classes this fake build contains: a 1.21.4-like build with PvP but no spear or mace code. */
    private final Set<String> engineClasses = new java.util.HashSet<>(Set.of(
            "baritone.process.PvpProcess", "baritone.process.PveProcess"));

    @BeforeEach
    void setUp() {
        baritone = mock(IBaritone.class);
        pathing = mock(IPathingBehavior.class);
        control = mock(IPathingControlManager.class);
        customGoal = mock(ICustomGoalProcess.class);
        inputs = mock(IInputOverrideHandler.class);
        when(baritone.getPathingBehavior()).thenReturn(pathing);
        when(baritone.getPathingControlManager()).thenReturn(control);
        when(baritone.getCustomGoalProcess()).thenReturn(customGoal);
        when(baritone.getInputOverrideHandler()).thenReturn(inputs);
        when(pathing.getInProgress()).thenReturn(Optional.empty());
        when(control.mostRecentInControl()).thenReturn(Optional.empty());

        contexts = new ContextTracker(sink);
        lifecycle = new LifecycleListener(contexts, game, sink);
        OstinatoRuntimeIntegration runtime = new OstinatoRuntimeIntegration(game, lifecycle,
                new OstinatoSignals(baritone, secondary), secondary, null,
                new EngineFeatures(engineClasses::contains));
        conductor = new ContractService(runtime, contexts, sink);
    }

    private void tick(TickEvent.Type type) {
        lifecycle.onTick(new TickEvent(EventState.PRE, type, tickCount++));
    }

    private <T> Observation<T> observe(conductor.contract.ObservationKind<T> kind) {
        ObservationResult<T> result = conductor.observe(kind, FreshnessPolicy.sameTick());
        assertTrue(result.isAccepted(), result::toString);
        return result.observation().orElseThrow();
    }

    // --- read-only signal mapping ---

    @Test
    void pathingSignalsAreReadOnly() {
        tick(TickEvent.Type.IN);
        when(pathing.getCurrent()).thenReturn(mock(IPathExecutor.class));
        IBaritoneProcess proc = mock(IBaritoneProcess.class);
        when(control.mostRecentInControl()).thenReturn(Optional.of(proc));
        when(customGoal.isActive()).thenReturn(true);
        when(inputs.isInputForcedDown(any())).thenAnswer(inv -> inv.getArgument(0) == Input.MOVE_FORWARD);

        assertEquals(Optional.of(true), observe(ObservationKinds.PATH_SEGMENT_PRESENT).observedValue());
        assertEquals(Optional.of(false), observe(ObservationKinds.NEXT_SEGMENT_PRESENT).observedValue());
        assertEquals(Optional.of(false), observe(ObservationKinds.PATH_CALCULATION_PENDING).observedValue());
        assertEquals(Optional.of(Optional.of(proc.getClass().getName())), observe(ObservationKinds.CONTROLLING_PROCESS).observedValue());
        assertEquals(Optional.of(true), observe(ObservationKinds.TRAVEL_GOAL_ACTIVE).observedValue());
        assertEquals(Optional.of(Set.of("MOVE_FORWARD")), observe(ObservationKinds.FORCED_INPUTS).observedValue());

        // Only the read accessors were touched: no cancel, path, setGoal, setInputForceState, clearAllKeys, ...
        verify(pathing).getCurrent();
        verify(pathing).getNext();
        verify(pathing).getInProgress();
        verify(control).mostRecentInControl();
        verify(customGoal).isActive();
        verify(inputs, atLeastOnce()).isInputForcedDown(any());
        verifyNoMoreInteractions(pathing, control, customGoal, inputs);
        verifyNoInteractions(proc);
    }

    @Test
    void playerPositionComesFromGameAccessAndNeverDefaults() {
        tick(TickEvent.Type.IN);

        assertEquals(Optional.of(new conductor.contract.BlockPoint(1, 64, -2)), observe(ObservationKinds.PLAYER_POSITION).observedValue());
        game.player = null;
        assertEquals(Availability.NOT_OBSERVED, observe(ObservationKinds.PLAYER_POSITION).availability());
        verifyNoInteractions(pathing, control, customGoal, inputs);
    }

    @Test
    void secondaryBackendFalseIsNeverAConfirmedNegative() {
        tick(TickEvent.Type.IN);

        assertEquals(Optional.of(false), observe(ObservationKinds.SECONDARY_BACKEND_BOUND).observedValue());
        assertEquals(Availability.NOT_APPLICABLE, observe(ObservationKinds.SECONDARY_BACKEND_PATHING).availability());

        secondary.bound = true;
        assertEquals(Availability.UNKNOWN, observe(ObservationKinds.SECONDARY_BACKEND_PATHING).availability());
        assertEquals(Availability.UNKNOWN, observe(ObservationKinds.SECONDARY_BACKEND_STOPPING).availability());

        secondary.pathing = true;
        assertEquals(Optional.of(true), observe(ObservationKinds.SECONDARY_BACKEND_PATHING).observedValue());
    }

    @Test
    void discoveryReportsBackendAvailabilityAndNoOperationsWithoutNavigation() {
        tick(TickEvent.Type.IN);
        DiscoveryResult result = conductor.discover();

        CapabilityDescriptor secondaryCap = result.capabilities().stream()
                .filter(c -> c.id().equals(OstinatoRuntimeIntegration.SECONDARY_BACKEND)).findFirst().orElseThrow();
        assertEquals(CapabilityAvailability.UNAVAILABLE, secondaryCap.availability());
        result.capabilities().stream().filter(c -> !c.id().equals(OstinatoRuntimeIntegration.CONTENT))
                .forEach(c -> assertTrue(c.operations().isEmpty(), c.id()));
        verifyNoInteractions(pathing, control, customGoal, inputs);
    }

    // --- context identity from lifecycle events ---

    @Test
    void lifecycleEventsDriveContextIdentity() {
        assertEquals(Optional.empty(), contexts.current());
        tick(TickEvent.Type.IN);
        ContextId first = contexts.current().orElseThrow();

        game.dimension = "minecraft:the_nether";
        tick(TickEvent.Type.IN);
        ContextId nether = contexts.current().orElseThrow();
        assertEquals(first.sessionId(), nether.sessionId());
        assertNotEquals(first.epoch(), nether.epoch());

        lifecycle.onWorldEvent(new WorldEvent(null, EventState.PRE));
        assertEquals(Optional.of(nether), contexts.current(), "PRE world events are ignored");
        lifecycle.onWorldEvent(new WorldEvent(null, EventState.POST));
        assertEquals(Optional.empty(), contexts.current());

        tick(TickEvent.Type.IN);
        ContextId again = contexts.current().orElseThrow();
        game.connection = new Object();
        tick(TickEvent.Type.IN);
        assertNotEquals(again.sessionId(), contexts.current().orElseThrow().sessionId());

        tick(TickEvent.Type.OUT);
        assertEquals(Optional.empty(), contexts.current());
    }

    @Test
    void observationFromBeforeADimensionChangeIsRejected() {
        tick(TickEvent.Type.IN);
        FreshnessPolicy policy = FreshnessPolicy.maxAge(OptionalLong.of(100), Optional.empty());
        Observation<Boolean> before = conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, policy).observation().orElseThrow();

        game.dimension = "minecraft:the_end";
        tick(TickEvent.Type.IN);

        assertEquals(Optional.of(RejectionReason.CONTEXT_INVALIDATED), conductor.revalidate(before, policy).rejection());
    }

    @Test
    void observationsAreRejectedUntilTheFirstInWorldTick() {
        assertEquals(Optional.of(RejectionReason.NO_CURRENT_CONTEXT),
                conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick()).rejection());
        verifyNoInteractions(pathing);
    }

    // --- off-thread ---

    @Test
    void offThreadCallsTouchNoEngineStateAndAreRecorded() throws InterruptedException {
        tick(TickEvent.Type.IN);
        ContextId before = contexts.current().orElseThrow();
        AtomicReference<ObservationResult<Boolean>> result = new AtomicReference<>();
        Thread other = new Thread(() -> {
            result.set(conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick()));
            lifecycle.onTick(new TickEvent(EventState.PRE, TickEvent.Type.OUT, 999));
        }, "worker");
        other.start();
        other.join();

        assertEquals(Optional.of(RejectionReason.OFF_THREAD), result.get().rejection());
        assertEquals(Optional.of(before), contexts.current(), "off-thread lifecycle event did not move the context");
        verifyNoInteractions(pathing, control, customGoal, inputs);
        assertEquals(2, sink.snapshot().stream().filter(e -> e.type() == DiagnosticEvent.Type.OFF_THREAD_VIOLATION).count());
    }

    // --- content detection ---

    @Test
    void registryContentIsPresentOrAbsentOnlyAfterLooking() {
        game.registry.add("ITEM:minecraft:mace");
        assertEquals(ContentResult.Presence.PRESENT, conductor.content(ContentId.item("minecraft:mace")).presence());
        assertEquals(ContentResult.Presence.ABSENT, conductor.content(ContentId.item("minecraft:iron_spear")).presence());
        assertEquals(ContentResult.Presence.UNKNOWN, conductor.content(ContentId.item("not an id")).presence(),
                "a malformed id is UNKNOWN, never ABSENT");
        verifyNoInteractions(pathing, control, customGoal, inputs);
    }

    @Test
    void engineFeaturesFollowTheBuildAndUnknownIdsStayUnknown() {
        assertEquals(ContentResult.Presence.PRESENT, conductor.content(ContentId.engine("ostinato:pvp")).presence());
        assertEquals(ContentResult.Presence.ABSENT, conductor.content(ContentId.engine("ostinato:combat/spear")).presence());
        assertEquals(ContentResult.Presence.UNKNOWN, conductor.content(ContentId.engine("ostinato:teleport")).presence());

        engineClasses.add("baritone.process.CombatSpear");
        assertEquals(ContentResult.Presence.PRESENT, conductor.content(ContentId.engine("ostinato:combat/spear")).presence());
    }

    @Test
    void discoveryListsTheEngineFeaturesPresent() {
        tick(TickEvent.Type.IN);
        CapabilityDescriptor content = conductor.discover().capabilities().stream()
                .filter(c -> c.id().equals(OstinatoRuntimeIntegration.CONTENT)).findFirst().orElseThrow();
        assertTrue(content.detail().contains("[ostinato:pvp, ostinato:pve]"), content.detail());
    }

    @Test
    void realProbeFindsTheCombatProcessesEveryTargetBuildHas() {
        // These tests run against each target's Ostinato build; PvP and PvE exist in all of them.
        EngineFeatures real = EngineFeatures.ofLoader(IBaritone.class.getClassLoader());
        for (String id : java.util.List.of("ostinato:pvp", "ostinato:pve")) {
            assertEquals(ContentResult.Presence.PRESENT, real.check(ContentId.engine(id)).presence(), id);
        }
    }

    @Test
    void inventoryIsReportedByRegistryIdAndNotObservedWithoutAPlayer() {
        tick(TickEvent.Type.IN);
        game.inventory.put("minecraft:shield", 1);
        assertEquals(Optional.of(java.util.Map.of("minecraft:iron_sword", 1, "minecraft:shield", 1)),
                observe(ObservationKinds.INVENTORY_ITEMS).observedValue());
        game.inventory = null;
        assertEquals(Availability.NOT_OBSERVED, observe(ObservationKinds.INVENTORY_ITEMS).availability());
    }
}

