package conductor.engine.ostinato;

import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
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
import conductor.contract.ContentResult;
import conductor.contract.DiscoveryResult;
import conductor.contract.FreshnessPolicy;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.Observation;
import conductor.contract.ObservationKinds;
import conductor.contract.ObservationResult;
import conductor.contract.ReleasePolicy;
import conductor.runtime.ConductorRuntime;
import conductor.runtime.EngineLayerProvider;
import conductor.runtime.FakeGameAccess;
import conductor.runtime.TestRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The Ostinato engine layer: read-only signal mapping, discovery, and how Conductor finds it. Ostinato's API is
 * mocked, so any call other than the expected reads fails the test.
 */
class OstinatoLayerTest {

    private final FakeGameAccess game = new FakeGameAccess();
    private final FakeSecondaryBackend secondary = new FakeSecondaryBackend();

    private IPathingBehavior pathing;
    private IPathingControlManager control;
    private ICustomGoalProcess customGoal;
    private IInputOverrideHandler inputs;
    private ConductorRuntime.Installed installed;
    private Conductor conductor;

    @BeforeEach
    void setUp() {
        IBaritone baritone = mock(IBaritone.class);
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

        // No navigation or combat ports: only the signals are under test, and no release policy runs evidence reads.
        OstinatoLayer layer = new OstinatoLayer(new OstinatoSignals(baritone, secondary), secondary, null, null, null);
        installed = TestRuntime.install(new InMemoryDiagnosticSink(1000), ReleasePolicy.none(), game, layer);
        conductor = installed.service();
    }

    /** One in-world tick. The contract's tick reads engine signals for release evidence (reads only); forget those. */
    private void tick() {
        installed.tick().run();
        clearInvocations(pathing, control, customGoal, inputs);
    }

    private <T> Observation<T> observe(conductor.contract.ObservationKind<T> kind) {
        ObservationResult<T> result = conductor.observe(kind, FreshnessPolicy.sameTick());
        assertTrue(result.isAccepted(), result::toString);
        return result.observation().orElseThrow();
    }

    @Test
    void pathingSignalsAreReadOnly() {
        tick();
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
    void coreReadsNeverTouchTheEngine() {
        tick();
        assertEquals(Optional.of(new conductor.contract.BlockPoint(1, 64, -2)), observe(ObservationKinds.PLAYER_POSITION).observedValue());
        verifyNoInteractions(pathing, control, customGoal, inputs);
    }

    @Test
    void secondaryBackendFalseIsNeverAConfirmedNegative() {
        tick();

        assertEquals(Optional.of(false), observe(ObservationKinds.SECONDARY_BACKEND_BOUND).observedValue());
        assertEquals(Availability.NOT_APPLICABLE, observe(ObservationKinds.SECONDARY_BACKEND_PATHING).availability());

        secondary.bound = true;
        assertEquals(Availability.UNKNOWN, observe(ObservationKinds.SECONDARY_BACKEND_PATHING).availability());
        assertEquals(Availability.UNKNOWN, observe(ObservationKinds.SECONDARY_BACKEND_STOPPING).availability());

        secondary.pathing = true;
        assertEquals(Optional.of(true), observe(ObservationKinds.SECONDARY_BACKEND_PATHING).observedValue());
    }

    @Test
    void discoveryAddsTheEngineToTheCoreWithoutOperationsItCannotRun() {
        tick();
        DiscoveryResult result = conductor.discover();

        CapabilityDescriptor secondaryCap = result.capabilities().stream()
                .filter(c -> c.id().equals(OstinatoLayer.SECONDARY_BACKEND)).findFirst().orElseThrow();
        assertEquals(CapabilityAvailability.UNAVAILABLE, secondaryCap.availability());
        for (String id : new String[]{OstinatoLayer.NAVIGATION, OstinatoLayer.COMBAT}) {
            CapabilityDescriptor c = result.capabilities().stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
            assertTrue(c.operations().isEmpty(), id);
        }
        assertTrue(result.capabilities().stream().anyMatch(c -> c.id().equals("player")), "core capabilities stay");
        assertEquals("Ostinato", installed.engine());
        verifyNoInteractions(pathing, control, customGoal, inputs);
    }

    @Test
    void conductorFindsTheLayerThroughItsServiceFileWhenOstinatoIsPresent() {
        EngineLayerProvider provider = ServiceLoader.load(EngineLayerProvider.class, getClass().getClassLoader())
                .findFirst().orElseThrow();
        assertTrue(provider instanceof OstinatoLayerProvider);
        assertTrue(provider.available(getClass().getClassLoader()));
        assertTrue(!provider.available(new ClassLoader(null) {}), "no Ostinato classes, not available");
    }

    @Test
    void realProbeFindsTheCombatProcessesEveryTargetBuildHas() {
        // These tests run against each target's Ostinato build; PvP and PvE exist in all of them.
        for (String id : java.util.List.of("ostinato:pvp", "ostinato:pve")) {
            assertEquals(ContentResult.Presence.PRESENT, TestRuntime.engineFeature(IBaritone.class.getClassLoader(), id), id);
        }
    }
}
