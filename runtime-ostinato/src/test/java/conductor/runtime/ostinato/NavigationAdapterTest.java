package conductor.runtime.ostinato;

import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.IEventBus;
import baritone.api.event.listener.IGameEventListener;
import baritone.api.movement.IMovementEngine;
import baritone.api.movement.MovementBackendKind;
import baritone.api.movement.MovementFailureReason;
import baritone.api.movement.MovementGoal;
import baritone.api.movement.MovementStatus;
import baritone.api.movement.PathResult;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.input.Input;
import conductor.contract.Conductor;
import conductor.contract.DiagnosticEvent;
import conductor.contract.DiscoveryResult;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.NavigationGoal;
import conductor.contract.OperationId;
import conductor.contract.OperationState;
import conductor.contract.OperationStatus;
import conductor.contract.RejectionReason;
import conductor.contract.ReleasePolicy;
import conductor.contract.ResourceState;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.Invocation;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Stages 3 and 4 through the real Ostinato adapter wiring ({@code ConductorOstinato.install}, the lifecycle
 * listener, {@link OstinatoSignals}). The engine and Baritone are Mockito mocks of Ostinato's API, so these tests
 * check calls and evidence at the boundary. Live behaviour is in docs/live-run-2026-10-12.md.
 */
class NavigationAdapterTest {

    private static final int WINDOW = 3;

    private final FakeGameAccess game = new FakeGameAccess();
    private final FakeSecondaryBackend secondary = new FakeSecondaryBackend();
    private final InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(10_000);
    private IMovementEngine engine;
    private IPathingBehavior pathing;
    private IPathingControlManager control;
    private ICustomGoalProcess customGoal;
    private IInputOverrideHandler inputs;
    private IBaritone baritone;
    private boolean prefersTungsten;
    private IGameEventListener lifecycle;
    private Conductor conductor;
    private int tickCount;

    @BeforeEach
    void setUp() {
        engine = mock(IMovementEngine.class);
        when(engine.goTo(any(MovementGoal.class))).thenReturn(PathResult.accepted(MovementBackendKind.BARITONE, MovementStatus.PATHING));
        pathing = mock(IPathingBehavior.class);
        control = mock(IPathingControlManager.class);
        customGoal = mock(ICustomGoalProcess.class);
        inputs = mock(IInputOverrideHandler.class);
        baritone = mock(IBaritone.class);
        IEventBus bus = mock(IEventBus.class);
        when(baritone.getPathingBehavior()).thenReturn(pathing);
        when(baritone.getPathingControlManager()).thenReturn(control);
        when(baritone.getCustomGoalProcess()).thenReturn(customGoal);
        when(baritone.getInputOverrideHandler()).thenReturn(inputs);
        when(baritone.getGameEventHandler()).thenReturn(bus);
        quiet();

        conductor = ConductorOstinato.install(baritone, sink, new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.empty()),
                game, secondary, engine, () -> prefersTungsten);
        ArgumentCaptor<IGameEventListener> registered = ArgumentCaptor.forClass(IGameEventListener.class);
        verify(bus).registerEventListener(registered.capture());
        lifecycle = registered.getValue();
        ticks(WINDOW);
    }

    private void quiet() {
        when(pathing.getCurrent()).thenReturn(null);
        when(pathing.getNext()).thenReturn(null);
        when(pathing.getInProgress()).thenReturn(Optional.empty());
        when(control.mostRecentInControl()).thenReturn(Optional.empty());
        when(customGoal.isActive()).thenReturn(false);
        when(inputs.isInputForcedDown(any())).thenReturn(false);
    }

    /** Engine executing a segment and holding forward, as seen live after cancel (finding L1). */
    private void segmentRunning() {
        when(pathing.getCurrent()).thenReturn(mock(IPathExecutor.class));
        when(inputs.isInputForcedDown(any())).thenAnswer(inv -> inv.getArgument(0) == Input.MOVE_FORWARD);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            lifecycle.onTick(new TickEvent(EventState.PRE, TickEvent.Type.IN, tickCount++));
        }
    }

    private OperationId travel(NavigationGoal goal) {
        var result = conductor.requestTravel(goal);
        assertTrue(result.isAccepted(), result::toString);
        segmentRunning();
        return result.status().orElseThrow().id();
    }

    private OperationState state(OperationId id) {
        return conductor.operationStatus(id).status().orElseThrow().state();
    }

    private static List<String> engineCalls(IMovementEngine mock) {
        return mockingDetails(mock).getInvocations().stream().map(NavigationAdapterTest::describe).toList();
    }

    private static String describe(Invocation invocation) {
        String name = invocation.getMethod().getName();
        if (name.equals("goTo")) {
            MovementGoal goal = invocation.getArgument(0);
            return "goTo " + goal.getKind() + " " + goal.primaryBlock();
        }
        return name;
    }

    // --- compare to direct use ---

    @Test
    void delegationMakesTheSameEngineCallsAsDirectUse() {
        IMovementEngine direct = mock(IMovementEngine.class);
        when(direct.goTo(any(MovementGoal.class))).thenReturn(PathResult.accepted(MovementBackendKind.BARITONE, MovementStatus.PATHING));
        direct.goTo(MovementGoal.getToBlock(new BlockPos(10, 64, -5)));
        direct.cancel();

        conductor.cancel(travel(NavigationGoal.getToBlock(10, 64, -5)));

        assertEquals(engineCalls(direct), engineCalls(engine));
    }

    @Test
    void everyGoalKindMapsToTheMatchingMovementGoal() {
        assertEquals(MovementGoal.Kind.BLOCK, OstinatoNavigationPort.toMovementGoal(NavigationGoal.block(1, 2, 3)).getKind());
        assertEquals(new BlockPos(1, 2, 3), OstinatoNavigationPort.toMovementGoal(NavigationGoal.getToBlock(1, 2, 3)).getBlock());
        assertTrue(OstinatoNavigationPort.toMovementGoal(NavigationGoal.near(1, 2, 3, 4)).toString().contains("range=4"));
        assertTrue(OstinatoNavigationPort.toMovementGoal(NavigationGoal.xz(7, -9)).toString().contains("xz=(7,-9)"));
    }

    @Test
    void contractOnlyEverCallsGoToAndCancelOnTheEngine() {
        OperationId id = travel(NavigationGoal.xz(1, 1));
        ticks(10);
        conductor.cancel(id);
        quiet();
        ticks(WINDOW + 2);

        verify(engine).goTo(any(MovementGoal.class));
        verify(engine).cancel();
        verifyNoMoreInteractions(engine);
    }

    // --- Stage 4 through real adapter signals ---

    @Test
    void cancelIsConfirmedOnlyAfterTheSegmentAndInputsClearForTheWindow() {
        OperationId id = travel(NavigationGoal.getToBlock(5, 70, 5));
        ticks(5);
        conductor.cancel(id);
        ticks(80);
        assertEquals(OperationState.CANCELLING, state(id), "segment still executing after cancel");
        assertEquals(Optional.of(RejectionReason.CONFLICTING_OPERATION), conductor.requestTravel(NavigationGoal.xz(0, 0)).rejection());

        quiet();
        ticks(WINDOW);
        OperationStatus done = conductor.operationStatus(id).status().orElseThrow();
        assertEquals(OperationState.CANCELLED, done.state());
        assertFalse(done.authorityHeld());
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
        assertTrue(conductor.requestTravel(NavigationGoal.xz(0, 0)).isAccepted(), "reuse on evidence");
        verify(engine, times(2)).goTo(any(MovementGoal.class));
    }

    @Test
    void foreignProcessIsDetectedAndBlocksTheGrant() {
        IBaritoneProcess mine = mock(IBaritoneProcess.class);
        when(control.mostRecentInControl()).thenReturn(Optional.of(mine));
        when(inputs.isInputForcedDown(any())).thenAnswer(inv -> inv.getArgument(0) == Input.CLICK_LEFT);
        ticks(WINDOW * 3);

        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(NavigationGoal.xz(0, 0)).rejection());
        verifyNoInteractions(engine);
        assertTrue(sink.snapshot().stream().anyMatch(e -> e.type() == DiagnosticEvent.Type.INTERFERENCE && e.subject().equals("DETECTED")));
        verifyNoInteractions(mine);
    }

    @Test
    void boundTungstenBlocksRelease() {
        secondary.bound = true;
        ticks(50);

        assertEquals(ResourceState.QUARANTINED, conductor.resourceStatus().state());
    }

    // --- fail-closed routing and decline ---

    @Test
    void tungstenRouteIsRejectedWithoutTouchingTheEngine() {
        prefersTungsten = true;

        assertEquals(Optional.of(RejectionReason.BACKEND_UNOBSERVABLE), conductor.requestTravel(NavigationGoal.getToBlock(0, 64, 0)).rejection());
        verifyNoInteractions(engine);
    }

    @Test
    void engineReportingTungstenMakesTheOperationUncertain() {
        when(engine.goTo(any(MovementGoal.class))).thenReturn(PathResult.accepted(MovementBackendKind.TUNGSTEN, MovementStatus.PATHING));

        assertEquals(OperationState.UNCERTAIN, state(travel(NavigationGoal.getToBlock(0, 64, 0))));
    }

    @Test
    void engineDeclineLeavesTheResourceQuarantinedUntilEvidence() {
        when(engine.goTo(any(MovementGoal.class))).thenReturn(PathResult.failed(MovementFailureReason.EXCEPTION, MovementBackendKind.BARITONE, "boom"));

        assertEquals(Optional.of(RejectionReason.ENGINE_DECLINED), conductor.requestTravel(NavigationGoal.xz(0, 0)).rejection());
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(NavigationGoal.xz(0, 0)).rejection());
        ticks(WINDOW);
        assertEquals(Optional.of(RejectionReason.ENGINE_DECLINED), conductor.requestTravel(NavigationGoal.xz(0, 0)).rejection());
    }

    // --- lifecycle ---

    @Test
    void dimensionChangeCancelsBestEffortAndGrantsNothingUntilFreshEvidence() {
        OperationId id = travel(NavigationGoal.getToBlock(5, 70, 5));

        game.dimension = "minecraft:the_nether";
        ticks(1);

        verify(engine).cancel();
        OperationStatus status = conductor.operationStatus(id).status().orElseThrow();
        assertEquals(OperationState.CANCELLING, status.state());
        assertFalse(status.authorityHeld());
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(NavigationGoal.xz(5, 5)).rejection());
    }

    @Test
    void discoveryListsNavigationOperations() {
        DiscoveryResult result = conductor.discover();

        assertEquals(OstinatoRuntimeIntegration.NAVIGATION_OPERATIONS, result.capabilities().stream()
                .filter(c -> c.id().equals(OstinatoRuntimeIntegration.NAVIGATION)).findFirst().orElseThrow().operations());
    }

    @Test
    void secondInstallOnTheSameBaritoneIsRefused() {
        assertThrows(IllegalStateException.class, () -> ConductorOstinato.install(baritone, sink, ReleasePolicy.none(),
                game, secondary, engine, () -> false));
    }
}
