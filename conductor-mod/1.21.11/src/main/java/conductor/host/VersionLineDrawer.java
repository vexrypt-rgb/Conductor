package conductor.host;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import conductor.host.mixin.RenderPipelinesAccessor;
import conductor.host.mixin.RenderTypeInvoker;
import conductor.runtime.LineDrawer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.List;

/** 1.21.11 line drawing: vanilla's line render type, plus one without a depth test for "through walls". */
public final class VersionLineDrawer implements LineDrawer {

    /** What the render hook passes per frame. */
    public record Frame(Matrix4f modelView, Camera camera) {}

    private static final float WIDTH = 2.0F;
    private RenderType noDepth;

    @Override
    public double[] camera(Object frame) {
        Camera camera = ((Frame) frame).camera();
        Vec3 p = camera.position();
        Vector3fc f = camera.forwardVector();
        return new double[]{p.x, p.y, p.z, f.x(), f.y(), f.z()};
    }

    private RenderType noDepth() {
        if (noDepth == null) {
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccessor.conductor$linesSnippet())
                    .withLocation("pipeline/conductor_lines_no_depth")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build();
            noDepth = RenderTypeInvoker.conductor$create("conductor_lines_no_depth",
                    RenderSetup.builder(pipeline).bufferSize(1536).createRenderSetup());
        }
        return noDepth;
    }

    @Override
    public void draw(Object frame, List<LineBatch> batches) {
        PoseStack stack = new PoseStack();
        stack.mulPose(((Frame) frame).modelView());
        PoseStack.Pose pose = stack.last();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (LineBatch batch : batches) {
            RenderType type = batch.throughWalls() ? noDepth() : RenderTypes.lines();
            VertexConsumer out = buffers.getBuffer(type);
            float a = ((batch.argb() >>> 24) & 0xFF) / 255F;
            float r = ((batch.argb() >>> 16) & 0xFF) / 255F;
            float g = ((batch.argb() >>> 8) & 0xFF) / 255F;
            float b = (batch.argb() & 0xFF) / 255F;
            float[] s = batch.segments();
            for (int i = 0; i + 5 < s.length; i += 6) {
                float dx = s[i + 3] - s[i], dy = s[i + 4] - s[i + 1], dz = s[i + 5] - s[i + 2];
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len == 0) {
                    continue;
                }
                out.addVertex(pose, s[i], s[i + 1], s[i + 2]).setColor(r, g, b, a)
                        .setNormal(pose, dx / len, dy / len, dz / len).setLineWidth(WIDTH);
                out.addVertex(pose, s[i + 3], s[i + 4], s[i + 5]).setColor(r, g, b, a)
                        .setNormal(pose, dx / len, dy / len, dz / len).setLineWidth(WIDTH);
            }
            buffers.endBatch(type);
        }
    }
}
