package conductor.runtime.ostinato;

import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.IEventBus;
import baritone.api.event.listener.IGameEventListener;
import baritone.api.movement.IMovementEngine;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.utils.IInputOverrideHandler;
import conductor.contract.CapabilityAvailability;
import conductor.contract.CapabilityDescriptor;
import conductor.contract.CombatGoal;
import conductor.contract.Conductor;
import conductor.contract.FreshnessPolicy;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.NavigationGoal;
import conductor.contract.ObservationKinds;
import conductor.contract.OperationId;
import conductor.contract.OperationState;
import conductor.contract.RejectionReason;
import conductor.contract.ReleasePolicy;
import conductor.contract.ResourceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Stage 5 adapter tests: combat routing, the use key as release evidence, and the target observation. */
class CombatAdapterTest {

    private static final int WINDOW = 3;

    /** Records the engine combat calls the adapter makes. */
    static final class FakeCombatAccess implements CombatAccess {
        final List<String> calls = new ArrayList<>();
        Optional<String> target = Optional.empty();

        public void attackPlayer(String name) { calls.add("pvp player " + name); }
        public void attackPlayers() { calls.add("pvp players"); }
        public void attackHostiles() { calls.add("pve hostiles"); }
        public void attackMob(String entityId) { calls.add("pve mob " + entityId); }
        public Optional<String> target() { return target; }
    }

    private final FakeGameAccess game = new FakeGameAccess();
    private final FakeCombatAccess combat = new FakeCombatAccess();
    private final InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(10_000);
    private IMovementEngine engine;
    private IGameEventListener lifecycle;
    private Conductor conductor;
    private int tickCount;

    @BeforeEach
    void setUp() {
        engine = mock(IMovementEngine.class);
        IPathingBehavior pathing = mock(IPathingBehavior.class);
        IPathingControlManager control = mock(IPathingControlManager.class);
        ICustomGoalProcess customGoal = mock(ICustomGoalProcess.class);
        IInputOverrideHandler inputs = mock(IInputOverrideHandler.class);
        IBaritone baritone = mock(IBaritone.class);
        IEventBus bus = mock(IEventBus.class);
        when(baritone.getPathingBehavior()).thenReturn(pathing);
        when(baritone.getPathingControlManager()).thenReturn(control);
        when(baritone.getCustomGoalProcess()).thenReturn(customGoal);
        when(baritone.getInputOverrideHandler()).thenReturn(inputs);
        when(baritone.getGameEventHandler()).thenReturn(bus);
        when(pathing.getInProgress()).thenReturn(Optional.empty());
        when(control.mostRecentInControl()).thenReturn(Optional.empty());
        when(inputs.isInputForcedDown(any())).thenReturn(false);

        conductor = ConductorOstinato.install(baritone, sink, new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.empty()),
                game, new FakeSecondaryBackend(), engine, () -> false, combat);
        ArgumentCaptor<IGameEventListener> registered = ArgumentCaptor.forClass(IGameEventListener.class);
        verify(bus).registerEventListener(registered.capture());
        lifecycle = registered.getValue();
        ticks(WINDOW);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            lifecycle.onTick(new TickEvent(EventState.PRE, TickEvent.Type.IN, tickCount++));
        }
    }

    private OperationState state(OperationId id) {
        return conductor.operationStatus(id).status().orElseThrow().state();
    }

    @Test
    void playersGoToPvpAndMobsToPve() {
        OperationId id = conductor.requestCombat(CombatGoal.player("Alex")).status().orElseThrow().id();
        conductor.cancel(id);
        ticks(WINDOW);
        conductor.requestCombat(CombatGoal.mob("minecraft:zombie"));
        assertEquals(List.of("pvp player Alex", "pve mob minecraft:zombie"), combat.calls);
    }

    @Test
    void stopUsesTheEngineCancelAndWaitsForTheUseKey() {
        OperationId id = conductor.requestCombat(CombatGoal.hostiles()).status().orElseThrow().id();
        game.useKey = true;                                    // shield up
        ticks(1);
        verify(engine, never()).cancel();
        conductor.cancel(id);
        verify(engine).cancel();
        ticks(WINDOW * 3);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state(), "use key still down");
        game.useKey = false;
        ticks(WINDOW);
        assertEquals(OperationState.CANCELLED, state(id));
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
    }

    @Test
    void anUnreadableUseKeyIsNeverQuiet() {
        game.useKey = null;
        ticks(WINDOW * 3);
        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
    }

    @Test
    void combatAndTravelShareTheMovementResource() {
        conductor.requestCombat(CombatGoal.hostiles());
        assertEquals(Optional.of(RejectionReason.CONFLICTING_OPERATION),
                conductor.requestTravel(NavigationGoal.xz(5, 5)).rejection());
    }

    @Test
    void theTargetIsObservedAndDiscoveryOffersCombat() {
        combat.target = Optional.of("minecraft:zombie Zombie");
        assertEquals(Optional.of(Optional.of("minecraft:zombie Zombie")),
                conductor.observe(ObservationKinds.COMBAT_TARGET, FreshnessPolicy.sameTick()).observation()
                        .flatMap(o -> o.observedValue()));
        CapabilityDescriptor cap = conductor.discover().capabilities().stream()
                .filter(c -> c.id().equals(OstinatoRuntimeIntegration.COMBAT)).findFirst().orElseThrow();
        assertEquals(CapabilityAvailability.AVAILABLE, cap.availability());
        assertEquals(OstinatoRuntimeIntegration.COMBAT_OPERATIONS, cap.operations());
    }

    @Test
    void ownControllersAreOstinatosCombatProcesses() {
        OstinatoCombatPort port = new OstinatoCombatPort(combat, engine);
        assertTrue(port.isOwnController("baritone.process.PvpProcess"));
        assertTrue(port.isOwnController("baritone.process.PveProcess"));
        assertTrue(!port.isOwnController("baritone.process.MineProcess"));
    }
}
