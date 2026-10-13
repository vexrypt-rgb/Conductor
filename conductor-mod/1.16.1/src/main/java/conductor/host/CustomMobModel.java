package conductor.host;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import conductor.runtime.ConductorMob;
import conductor.runtime.MobAnimations;
import conductor.runtime.MobGeometry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom mob's model for Minecraft 1.16.1, built from its Bedrock geometry ({@link MobGeometry} is already in Java
 * model space). 1.16's model parts cannot scale, so each bone is a part holding only its cubes, and this model moves,
 * turns and scales the bones itself, in the order later versions' parts do. Client only.
 */
public class CustomMobModel extends EntityModel<ConductorMob> {

    private final List<Bone> roots = new ArrayList<>();
    /** This frame's animation pose, set by the renderer before drawing. */
    final MobAnimations.Pose pose = new MobAnimations.Pose();

    private static final class Bone {
        final String name;
        final ModelPart cubes;
        final float[] offset;
        final float[] rotation;
        final List<Bone> children = new ArrayList<>();

        Bone(String name, ModelPart cubes, float[] offset, float[] rotation) {
            this.name = name;
            this.cubes = cubes;
            this.offset = offset;
            this.rotation = rotation;
        }
    }

    /** The model for {@code geometry}; with null it has no parts and draws nothing. */
    public CustomMobModel(MobGeometry geometry) {
        if (geometry == null) {
            return;
        }
        texWidth = geometry.textureWidth();
        texHeight = geometry.textureHeight();
        for (MobGeometry.Bone b : geometry.roots()) {
            roots.add(bone(b));
        }
    }

    private Bone bone(MobGeometry.Bone b) {
        ModelPart part = new ModelPart(this);
        for (MobGeometry.Cube c : b.cubes()) {
            part.texOffs(c.u(), c.v());
            part.addBox(c.min()[0], c.min()[1], c.min()[2], c.size()[0], c.size()[1], c.size()[2], c.inflate(), c.mirror());
        }
        Bone bone = new Bone(b.name(), part, b.offset(), b.rotation());
        for (MobGeometry.Bone child : b.children()) {
            bone.children.add(bone(child));
        }
        return bone;
    }

    @Override
    public void setupAnim(ConductorMob mob, float walkPosition, float walkSpeed, float ageInTicks, float headYaw, float headPitch) {
        // posed by the renderer, which has the partial tick
    }

    @Override
    public void renderToBuffer(PoseStack stack, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        for (Bone bone : roots) {
            render(bone, stack, buffer, light, overlay, r, g, b, a);
        }
    }

    private void render(Bone bone, PoseStack stack, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        float[] p = pose.bones().get(bone.name);
        float x = bone.offset[0], y = bone.offset[1], z = bone.offset[2];
        float xRot = bone.rotation[0], yRot = bone.rotation[1], zRot = bone.rotation[2];
        float xs = 1, ys = 1, zs = 1;
        if (p != null) {
            xRot += p[0];
            yRot += p[1];
            zRot += p[2];
            x += p[3];
            y += p[4];
            z += p[5];
            xs = p[6];
            ys = p[7];
            zs = p[8];
        }
        stack.pushPose();
        stack.translate(x / 16f, y / 16f, z / 16f);
        if (zRot != 0) {
            stack.mulPose(Vector3f.ZP.rotation(zRot));
        }
        if (yRot != 0) {
            stack.mulPose(Vector3f.YP.rotation(yRot));
        }
        if (xRot != 0) {
            stack.mulPose(Vector3f.XP.rotation(xRot));
        }
        if (xs != 1 || ys != 1 || zs != 1) {
            stack.scale(xs, ys, zs);
        }
        bone.cubes.render(stack, buffer, light, overlay, r, g, b, a);
        for (Bone child : bone.children) {
            render(child, stack, buffer, light, overlay, r, g, b, a);
        }
        stack.popPose();
    }
}
