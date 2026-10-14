package conductor.host;

import conductor.runtime.LineDrawer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 1.8.9 line drawing: fixed-function GL lines with textures off. The segments are camera-relative, so the view
 * rotation is set up here and there is no translation; depth test off for "through walls".
 */
public final class VersionLineDrawer implements LineDrawer {

    /** What the render hook passes per frame: the camera's rotation and position. */
    public static final class Frame {
        private final float pitch;
        private final float yaw;
        private final double x;
        private final double y;
        private final double z;

        public Frame(float pitch, float yaw, double x, double y, double z) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public float pitch() {
            return pitch;
        }

        public float yaw() {
            return yaw;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Frame)) return false;
            Frame other = (Frame) o;
            return java.util.Objects.equals(this.pitch, other.pitch) && java.util.Objects.equals(this.yaw, other.yaw) && java.util.Objects.equals(this.x, other.x) && java.util.Objects.equals(this.y, other.y) && java.util.Objects.equals(this.z, other.z);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(pitch, yaw, x, y, z);
        }

        @Override public String toString() {
            return "Frame[" + "pitch=" + pitch + ", yaw=" + yaw + ", x=" + x + ", y=" + y + ", z=" + z + "]";
        }
    }

    private static final float WIDTH = 2.0F;

    @Override
    public double[] camera(Object frame) {
        Frame f = (Frame) frame;
        double pitch = Math.toRadians(f.pitch());
        double yaw = Math.toRadians(f.yaw());
        // vanilla's view direction for this rotation
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        return new double[]{f.x(), f.y(), f.z(), fx, fy, fz};
    }

    @Override
    public void draw(Object frame, List<LineBatch> batches) {
        Frame f = (Frame) frame;
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.rotate(f.pitch(), 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(f.yaw() + 180.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA
        GL11.glLineWidth(WIDTH);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        try {
            for (LineBatch batch : batches) {
                if (batch.throughWalls()) {
                    GlStateManager.disableDepthTest();
                } else {
                    GlStateManager.enableDepthTest();
                }
                float a = ((batch.argb() >>> 24) & 0xFF) / 255F;
                float r = ((batch.argb() >>> 16) & 0xFF) / 255F;
                float g = ((batch.argb() >>> 8) & 0xFF) / 255F;
                float b = (batch.argb() & 0xFF) / 255F;
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
                float[] s = batch.segments();
                for (int i = 0; i + 5 < s.length; i += 6) {
                    buffer.vertex(s[i], s[i + 1], s[i + 2]).color(r, g, b, a).next();
                    buffer.vertex(s[i + 3], s[i + 4], s[i + 5]).color(r, g, b, a).next();
                }
                tessellator.draw();
            }
        } finally {
            GlStateManager.enableDepthTest();
            GlStateManager.enableCull();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.enableTexture();
            GlStateManager.popMatrix();
        }
    }
}
