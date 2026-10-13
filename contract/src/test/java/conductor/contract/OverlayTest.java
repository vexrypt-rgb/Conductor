package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Overlays: each module owns its shapes; the renderer gets the union; a stopped module's shapes disappear. */
class OverlayTest {

    private FakeRuntime runtime;
    private final List<List<OverlayShape>> shown = new ArrayList<>();
    private ModuleHost host;
    private ContractService core;
    private final ModuleContext[] a = new ModuleContext[1];
    private final ModuleContext[] b = new ModuleContext[1];

    @BeforeEach
    void setUp() {
        runtime = new FakeRuntime();
        runtime.port = new FakePort();
        runtime.overlayPort = shapes -> shown.add(shapes);
        runtime.quiet();
        InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(1000);
        ContextTracker contexts = new ContextTracker(sink);
        core = new ContractService(runtime, contexts, sink, new ReleasePolicy(OptionalLong.of(2), OptionalLong.empty()));
        contexts.onInWorld("minecraft:overworld", false, 0);
        host = new ModuleHost(core);
        host.register("a", module(a));
        host.register("b", module(b));
        runtime.tick++;
        core.tick();
        host.tick();
    }

    private static ConductorModule module(ModuleContext[] holder) {
        return new ConductorModule() {
            public ReleasePolicy releasePolicy() { return ReleasePolicy.none(); }
            public void onReady(ModuleContext c) { holder[0] = c; }
        };
    }

    private List<OverlayShape> last() {
        return shown.get(shown.size() - 1);
    }

    @Test
    void theRendererGetsEveryModulesShapesAndAStoppedModulesDisappear() {
        OverlayShape boxA = OverlayShape.block(new BlockPoint(0, 64, 0), 0xFFFF0000);
        OverlayShape tracerB = OverlayShape.tracer(5, 65, 5, 0xFF00FF00);
        assertTrue(a[0].showOverlay(List.of(boxA)).isAccepted());
        assertTrue(b[0].showOverlay(List.of(tracerB)).isAccepted());
        assertEquals(List.of(boxA, tracerB), last());

        host.management().disable(new ModuleId("a"), "test");
        assertEquals(List.of(tracerB), last());

        assertTrue(b[0].showOverlay(List.of()).isAccepted());
        assertEquals(List.of(), last());
    }

    @Test
    void shapesAreValidatedAndBounded() {
        assertThrows(IllegalArgumentException.class, () -> OverlayShape.line(0, Double.NaN, 0, 1, 1, 1, 0));
        List<OverlayShape> tooMany = Collections.nCopies(ContractService.MAX_SHAPES_PER_MODULE + 1, OverlayShape.tracer(0, 0, 0, 0));
        assertEquals(RejectionReason.INVALID_ARGUMENT, a[0].showOverlay(tooMany).rejection());
        assertTrue(OverlayShape.tracer(1, 2, 3, 0).throughWalls());
        assertTrue(!OverlayShape.block(new BlockPoint(1, 2, 3), 0).throughWalls());
    }

    @Test
    void aRuntimeWithoutOverlaysSaysSo() {
        runtime.overlayPort = null;
        assertEquals(RejectionReason.NO_ACTIONS, a[0].showOverlay(List.of()).rejection());
    }
}
