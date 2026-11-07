package conductor.runtime;

import conductor.contract.OverlayPort;
import conductor.contract.OverlayShape;

import java.util.List;

/**
 * Keeps the shapes modules want drawn and hands them to the per-version {@link LineDrawer} every frame, as
 * camera-relative line batches.
 */
final class OverlayRenderer implements OverlayPort {

    private volatile List<OverlayShape> shapes = Jdk8.listOf();
    private final LineDrawer drawer;

    OverlayRenderer(LineDrawer drawer) {
        this.drawer = drawer;
    }

    @Override
    public void show(List<OverlayShape> shapes) {
        this.shapes = Jdk8.copyList(shapes);
    }

    /** Render thread (the game thread), once per frame, from the render hook. */
    void render(Object frame) {
        List<OverlayShape> current = shapes;
        if (current.isEmpty()) {
            return;
        }
        drawer.draw(frame, OverlayGeometry.batches(current, drawer.camera(frame)));
    }
}
