package conductor.runtime.ostinato;

import baritone.api.IBaritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.IEventBus;
import baritone.api.movement.IMovementEngine;
import baritone.api.movement.MovementBackendKind;
import baritone.api.movement.MovementGoal;
import baritone.api.movement.MovementStatus;
import baritone.api.movement.PathResult;
import conductor.contract.Conductor;
import conductor.contract.ContextTracker;
import conductor.contract.ContractService;
import conductor.contract.DiscoveryResult;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.NavigationGoal;
import conductor.contract.OperationId;
import conductor.contract.OperationState;
import conductor.contract.OperationStatus;
import conductor.contract.RejectionReason;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

import java.util.List;
import java.util.Optional;

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
 * Stage 3 tests for single-consumer delegation to Ostinato's {@link IMovementEngine}. The engine is a Mockito mock,
 * so these tests compare calls at the boundary. Engine behaviour below the boundary is not exercised here; the real
 * {@code HybridMovementEngine} cannot be built off-game (its settings access constructs a full Baritone).
 */
class Stage3NavigationTest {

    private final FakeGameAccess game = new FakeGameAccess();
    private final FakeSecondaryBackend secondary = new FakeSecondaryBackend();
    private final InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(1000);
    private IMovementEngine engine;
    private boolean prefersTungsten;
    private LifecycleListener lifecycle;
    private Conductor conductor;
    private int tickCount;

    @BeforeEach
    void setUp() {
        engine = mock(IMovementEngine.class);
        when(engine.goTo(any(MovementGoal.class))).thenReturn(PathResult.accepted(MovementBackendKind.BARITONE, MovementStatus.PATHING));
        ContextTracker contexts = new ContextTracker(sink);
        lifecycle = new LifecycleListener(contexts, game, sink);
        OstinatoRuntimeIntegration runtime = new OstinatoRuntimeIntegration(game, lifecycle,
                new OstinatoSignals(mock(IBaritone.class), secondary), secondary,
                new OstinatoNavigationPort(engine, () -> prefersTungsten));
        conductor = new ContractService(runtime, contexts, sink);
        tick();
    }

    private void tick() {
        lifecycle.onTick(new TickEvent(EventState.PRE, TickEvent.Type.IN, tickCount++));
    }

    /** Engine calls as comparable strings: method name plus goal kind and target for goTo. */
    private static List<String> engineCalls(IMovementEngine mock) {
        return mockingDetails(mock).getInvocations().stream().map(Stage3NavigationTest::describe).toList();
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

        OperationId id = conductor.requestTravel(NavigationGoal.getToBlock(10, 64, -5)).status().orElseThrow().id();
        conductor.cancel(id);

        assertEquals(engineCalls(direct), engineCalls(engine));
        assertEquals(List.of("goTo GET_TO_BLOCK " + new BlockPos(10, 64, -5), "cancel"), engineCalls(engine));
    }

    @Test
    void everyGoalKindMapsToTheMatchingMovementGoal() {
        assertGoal(NavigationGoal.block(1, 2, 3), MovementGoal.Kind.BLOCK, new BlockPos(1, 2, 3));
        assertGoal(NavigationGoal.getToBlock(1, 2, 3), MovementGoal.Kind.GET_TO_BLOCK, new BlockPos(1, 2, 3));
        assertGoal(NavigationGoal.near(1, 2, 3, 4), MovementGoal.Kind.NEAR, new BlockPos(1, 2, 3));
        MovementGoal xz = OstinatoNavigationPort.toMovementGoal(NavigationGoal.xz(7, -9));
        assertEquals(MovementGoal.Kind.XZ, xz.getKind());
        assertTrue(xz.toString().contains("xz=(7,-9)"), xz.toString());
        assertTrue(OstinatoNavigationPort.toMovementGoal(NavigationGoal.near(1, 2, 3, 4)).toString().contains("range=4"));
    }

    private static void assertGoal(NavigationGoal goal, MovementGoal.Kind kind, BlockPos block) {
        MovementGoal mapped = OstinatoNavigationPort.toMovementGoal(goal);
        assertEquals(kind, mapped.getKind());
        assertEquals(block, mapped.getBlock());
    }

    @Test
    void contractReadsNothingFromTheEngineInstance() {
        OperationId id = conductor.requestTravel(NavigationGoal.xz(1, 1)).status().orElseThrow().id();
        conductor.operationStatus(id);
        conductor.cancel(id);

        // no status(), isPathing() or activeBackend(): engine self-reports are not evidence (stage1-audit S1)
        verify(engine).goTo(any(MovementGoal.class));
        verify(engine).cancel();
        verifyNoMoreInteractions(engine);
    }

    // --- fail-closed routing ---

    @Test
    void tungstenRouteIsRejectedWithoutTouchingTheEngine() {
        prefersTungsten = true;

        assertEquals(Optional.of(RejectionReason.BACKEND_UNOBSERVABLE),
                conductor.requestTravel(NavigationGoal.getToBlock(0, 64, 0)).rejection());
        verifyNoInteractions(engine);
    }

    @Test
    void engineReportingTungstenMakesTheOperationUncertain() {
        when(engine.goTo(any(MovementGoal.class))).thenReturn(PathResult.accepted(MovementBackendKind.TUNGSTEN, MovementStatus.PATHING));

        assertEquals(OperationState.UNCERTAIN,
                conductor.requestTravel(NavigationGoal.getToBlock(0, 64, 0)).status().orElseThrow().state());
    }

    @Test
    void engineDeclineIsReportedAndSpendsTheGrant() {
        when(engine.goTo(any(MovementGoal.class))).thenReturn(
                PathResult.failed(baritone.api.movement.MovementFailureReason.EXCEPTION, MovementBackendKind.BARITONE, "boom"));

        assertEquals(Optional.of(RejectionReason.ENGINE_DECLINED), conductor.requestTravel(NavigationGoal.xz(0, 0)).rejection());
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED), conductor.requestTravel(NavigationGoal.xz(0, 0)).rejection());
        verify(engine, times(1)).goTo(any(MovementGoal.class));
    }

    // --- lifecycle ---

    @Test
    void dimensionChangeCancelsBestEffortAndGrantsNothing() {
        OperationId id = conductor.requestTravel(NavigationGoal.getToBlock(5, 70, 5)).status().orElseThrow().id();

        game.dimension = "minecraft:the_nether";
        tick();

        verify(engine).cancel();
        OperationStatus status = conductor.operationStatus(id).status().orElseThrow();
        assertEquals(OperationState.CANCELLING, status.state());
        assertFalse(status.authorityHeld());
        assertEquals(Optional.of(RejectionReason.RESOURCE_NOT_RELEASED),
                conductor.requestTravel(NavigationGoal.getToBlock(5, 70, 5)).rejection());
        verify(engine, times(1)).goTo(any(MovementGoal.class));
    }

    @Test
    void discoveryListsNavigationOperations() {
        DiscoveryResult result = conductor.discover();

        assertEquals(OstinatoRuntimeIntegration.NAVIGATION_OPERATIONS, result.capabilities().stream()
                .filter(c -> c.id().equals(OstinatoRuntimeIntegration.NAVIGATION)).findFirst().orElseThrow().operations());
    }

    // --- single consumer ---

    @Test
    void secondInstallOnTheSameBaritoneIsRefused() {
        IBaritone baritone = mock(IBaritone.class);
        when(baritone.getGameEventHandler()).thenReturn(mock(IEventBus.class));

        ConductorOstinato.install(baritone, sink, game, secondary, engine, () -> false);
        assertThrows(IllegalStateException.class,
                () -> ConductorOstinato.install(baritone, sink, game, secondary, engine, () -> false));
    }
}
