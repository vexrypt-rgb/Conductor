package conductor.host;

import conductor.runtime.LineDrawer;
import net.minecraft.client.Camera;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.List;

/**
 * 26.2 line drawing: vanilla's gizmo lines (the ones its debug renderers use), emitted while the level is extracted for
 * the frame. "Through walls" lines are always-on-top gizmos. No render pipeline of Conductor's own.
 */
public final class VersionLineDrawer implements LineDrawer {

    /** What the render hook passes per frame. */
    public record Frame(Camera camera) {}

    private static final float WIDTH = 2.0F;

    @Override
    public double[] camera(Object frame) {
        Camera camera = ((Frame) frame).camera();
        Vec3 p = camera.position();
        Vector3fc f = camera.forwardVector();
        return new double[]{p.x, p.y, p.z, f.x(), f.y(), f.z()};
    }

    @Override
    public void draw(Object frame, List<LineBatch> batches) {
        Vec3 cam = ((Frame) frame).camera().position();
        for (LineBatch batch : batches) {
            float[] s = batch.segments();
            for (int i = 0; i + 5 < s.length; i += 6) {
                GizmoProperties line = Gizmos.line(cam.add(s[i], s[i + 1], s[i + 2]), cam.add(s[i + 3], s[i + 4], s[i + 5]),
                        batch.argb(), WIDTH);
                if (batch.throughWalls()) {
                    line.setAlwaysOnTop();
                }
            }
        }
    }
}
