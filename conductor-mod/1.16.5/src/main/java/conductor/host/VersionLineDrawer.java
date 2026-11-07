package conductor.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import conductor.runtime.LineDrawer;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 1.16.5 line drawing: the fixed-function pipeline still runs here, so plain coloured GL lines with textures off;
 * depth test off for "through walls".
 */
public final class VersionLineDrawer implements LineDrawer {

    /** What the render hook passes per frame: the camera rotation (the world pose) and the camera. */
    public record Frame(Matrix4f pose, Camera camera) {}

    private static final float WIDTH = 2.0F;

    @Override
    public double[] camera(Object frame) {
        Camera camera = ((Frame) frame).camera();
        Vec3 p = camera.getPosition();
        Vector3f f = camera.getLookVector();
        return new double[]{p.x, p.y, p.z, f.x(), f.y(), f.z()};
    }

    @Override
    public void draw(Object frame, List<LineBatch> batches) {
        Matrix4f pose = ((Frame) frame).pose();
        RenderSystem.pushMatrix();
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(WIDTH);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            for (LineBatch batch : batches) {
                if (batch.throughWalls()) {
                    RenderSystem.disableDepthTest();
                } else {
                    RenderSystem.enableDepthTest();
                }
                float a = ((batch.argb() >>> 24) & 0xFF) / 255F;
                float r = ((batch.argb() >>> 16) & 0xFF) / 255F;
                float g = ((batch.argb() >>> 8) & 0xFF) / 255F;
                float b = (batch.argb() & 0xFF) / 255F;
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder out = tesselator.getBuilder();
                out.begin(GL11.GL_LINES, DefaultVertexFormat.POSITION_COLOR);
                float[] s = batch.segments();
                for (int i = 0; i + 5 < s.length; i += 6) {
                    out.vertex(pose, s[i], s[i + 1], s[i + 2]).color(r, g, b, a).endVertex();
                    out.vertex(pose, s[i + 3], s[i + 4], s[i + 5]).color(r, g, b, a).endVertex();
                }
                tesselator.end();
            }
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableTexture();
            RenderSystem.popMatrix();
        }
    }
}
