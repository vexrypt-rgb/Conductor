package conductor.runtime;

import java.util.List;

/**
 * Puts overlay lines on the screen for one Minecraft version. Implemented by the Conductor mod next to its render
 * hook, because drawing needs per-version (and partly private) render APIs. {@code frame} is whatever that hook
 * passes to {@link Hooks#renderWorld}.
 */
public interface LineDrawer {

    /** Camera position and forward unit vector for this frame: {x, y, z, fx, fy, fz}. */
    double[] camera(Object frame);

    /** Draw these batches; segment coordinates are relative to the camera. */
    void draw(Object frame, List<LineBatch> batches);

    /**
     * Lines of one colour and depth mode.
     *
     * @param segments 6 floats per segment: x1, y1, z1, x2, y2, z2, relative to the camera
     */
    record LineBatch(int argb, boolean throughWalls, float[] segments) {}
}
