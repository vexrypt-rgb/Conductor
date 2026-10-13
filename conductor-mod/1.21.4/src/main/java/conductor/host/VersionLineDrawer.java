package conductor.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import conductor.runtime.LineDrawer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/** 1.21.4 line drawing: immediate-mode lines with vanilla's line shader; depth test off for "through walls". */
public final class VersionLineDrawer implements LineDrawer {

    /** What the render hook passes per frame. */
    public record Frame(Matrix4f modelView, Camera camera) {}

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
        PoseStack stack = new PoseStack();
        stack.mulPose(((Frame) frame).modelView());
        PoseStack.Pose pose = stack.last();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(CoreShaders.RENDERTYPE_LINES);
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
                BufferBuilder out = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                float[] s = batch.segments();
                for (int i = 0; i + 5 < s.length; i += 6) {
                    float dx = s[i + 3] - s[i], dy = s[i + 4] - s[i + 1], dz = s[i + 5] - s[i + 2];
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len == 0) {
                        continue;
                    }
                    out.addVertex(pose, s[i], s[i + 1], s[i + 2]).setColor(r, g, b, a).setNormal(pose, dx / len, dy / len, dz / len);
                    out.addVertex(pose, s[i + 3], s[i + 4], s[i + 5]).setColor(r, g, b, a).setNormal(pose, dx / len, dy / len, dz / len);
                }
                MeshData mesh = out.build();
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
            }
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }
}
