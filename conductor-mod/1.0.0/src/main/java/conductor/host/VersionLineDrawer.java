package conductor.host;

import conductor.runtime.LineDrawer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 1.0.0 line drawing: OpenGL 1 immediate mode, as 1.0 draws its own block outline. At the hook the view matrix holds
 * the camera's rotation only, so camera-relative coordinates are drawn as they are.
 */
public final class VersionLineDrawer implements LineDrawer {

    /** What the render hook passes per frame: the camera entity and how far the frame is into the tick. */
    public record Frame(Entity camera, float tickDelta) {}

    private static final float WIDTH = 2.0F;

    @Override
    public double[] camera(Object frame) {
        Frame f = (Frame) frame;
        Entity c = f.camera();
        float t = f.tickDelta();
        // as 1.0's block outline: the camera is where the entity is drawn this frame (y is at eye level in 1.0)
        double x = c.prevX + (c.x - c.prevX) * t;
        double y = c.prevY + (c.y - c.prevY) * t;
        double z = c.prevZ + (c.z - c.prevZ) * t;
        double yaw = Math.toRadians(c.lastYaw + (c.yaw - c.lastYaw) * t);
        double pitch = Math.toRadians(c.lastPitch + (c.pitch - c.lastPitch) * t);
        return new double[]{x, y, z, -Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
    }

    @Override
    public void draw(Object frame, List<LineBatch> batches) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT
                | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(WIDTH);
            GL11.glDepthMask(false);
            for (LineBatch batch : batches) {
                if (batch.throughWalls()) {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                } else {
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                }
                int argb = batch.argb();
                GL11.glColor4f(((argb >>> 16) & 0xFF) / 255F, ((argb >>> 8) & 0xFF) / 255F, (argb & 0xFF) / 255F,
                        ((argb >>> 24) & 0xFF) / 255F);
                float[] s = batch.segments();
                GL11.glBegin(GL11.GL_LINES);
                for (int i = 0; i + 5 < s.length; i += 6) {
                    GL11.glVertex3f(s[i], s[i + 1], s[i + 2]);
                    GL11.glVertex3f(s[i + 3], s[i + 4], s[i + 5]);
                }
                GL11.glEnd();
            }
        } finally {
            GL11.glPopAttrib(); // also restores the depth mask, the line width and the colour
        }
    }
}
