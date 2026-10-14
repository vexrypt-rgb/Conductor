package conductor.runtime;

import conductor.contract.BlockPoint;
import conductor.contract.OverlayShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Overlay shapes to camera-relative segments, and the renderer's hand-over to the per-version drawer. */
class OverlayGeometryTest {

    private static final double[] CAMERA = {10, 64, -5, 0, 0, 1};

    @Test
    void aBlockIsTwelveEdgesRelativeToTheCamera() {
        List<LineDrawer.LineBatch> batches = OverlayGeometry.batches(Jdk8.listOf(OverlayShape.block(new BlockPoint(10, 63, -5), 0xFFFF0000)), CAMERA);
        assertEquals(1, batches.size());
        float[] s = batches.get(0).segments();
        assertEquals(12 * 6, s.length);
        assertArrayEquals(new float[]{0, -1, 0, 1, -1, 0}, java.util.Arrays.copyOf(s, 6));
        for (float v : s) {
            assertTrue(v >= -1 && v <= 1, "within one block of the camera: " + v);
        }
    }

    @Test
    void aTracerStartsJustAheadOfTheCamera() {
        float[] s = OverlayGeometry.batches(Jdk8.listOf(OverlayShape.tracer(10, 64, 5, 0xFF00FF00)), CAMERA).get(0).segments();
        assertArrayEquals(new float[]{0, 0, (float) OverlayGeometry.TRACER_START, 0, 0, 10}, s);
    }

    @Test
    void shapesAreBatchedByColourAndDepthMode() {
        List<LineDrawer.LineBatch> batches = OverlayGeometry.batches(Jdk8.listOf(
                OverlayShape.line(0, 0, 0, 1, 1, 1, 0xFFFFFFFF),
                OverlayShape.line(0, 0, 0, 2, 2, 2, 0xFFFFFFFF),
                OverlayShape.line(0, 0, 0, 1, 1, 1, 0xFFFFFFFF).throughWalls(true),
                OverlayShape.line(0, 0, 0, 1, 1, 1, 0x80FFFFFF)), CAMERA);
        assertEquals(3, batches.size());
        assertEquals(12, batches.get(0).segments().length);
        assertFalse(batches.get(0).throughWalls());
        assertTrue(batches.get(1).throughWalls());
        assertEquals(0x80FFFFFF, batches.get(2).argb());
    }

    @Test
    void theRendererDrawsOnlyWhenThereIsSomethingToShow() {
        List<List<LineDrawer.LineBatch>> drawn = new ArrayList<>();
        OverlayRenderer renderer = new OverlayRenderer(new LineDrawer() {
            public double[] camera(Object frame) { return CAMERA; }
            public void draw(Object frame, List<LineBatch> batches) { drawn.add(batches); }
        });
        renderer.render("frame");
        assertTrue(drawn.isEmpty());
        renderer.show(Jdk8.listOf(OverlayShape.tracer(0, 0, 0, -1)));
        renderer.render("frame");
        assertEquals(1, drawn.size());
        renderer.show(Jdk8.listOf());
        renderer.render("frame");
        assertEquals(1, drawn.size());
    }
}
