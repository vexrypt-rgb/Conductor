package conductor.host;

import conductor.runtime.MobGeometry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * A custom mob's model, built with vanilla's own model parts from its Bedrock geometry ({@link MobGeometry} is already
 * in Java model space), and posed each frame from {@link CustomMobRenderState#pose}. Client only.
 */
public class CustomMobModel extends EntityModel<CustomMobRenderState> {

    private final Map<String, ModelPart> parts;

    private CustomMobModel(ModelPart root, Map<String, ModelPart> parts) {
        super(root);
        this.parts = parts;
    }

    /** The model for {@code geometry}, or one with no parts (draws nothing) for null. */
    public static CustomMobModel of(MobGeometry geometry) {
        MeshDefinition mesh = new MeshDefinition();
        if (geometry == null) {
            return new CustomMobModel(LayerDefinition.create(mesh, 16, 16).bakeRoot(), Map.of());
        }
        for (MobGeometry.Bone b : geometry.roots()) {
            add(mesh.getRoot(), b);
        }
        ModelPart root = LayerDefinition.create(mesh, geometry.textureWidth(), geometry.textureHeight()).bakeRoot();
        Map<String, ModelPart> parts = new HashMap<>();
        for (MobGeometry.Bone b : geometry.roots()) {
            collect(root.getChild(b.name()), b, parts);
        }
        return new CustomMobModel(root, parts);
    }

    private static void add(PartDefinition parent, MobGeometry.Bone bone) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (MobGeometry.Cube c : bone.cubes()) {
            cubes.texOffs(c.u(), c.v()).mirror(c.mirror()).addBox(c.min()[0], c.min()[1], c.min()[2],
                    c.size()[0], c.size()[1], c.size()[2], new CubeDeformation(c.inflate()));
        }
        float[] o = bone.offset();
        float[] r = bone.rotation();
        PartDefinition part = parent.addOrReplaceChild(bone.name(), cubes, PartPose.offsetAndRotation(o[0], o[1], o[2], r[0], r[1], r[2]));
        for (MobGeometry.Bone child : bone.children()) {
            add(part, child);
        }
    }

    private static void collect(ModelPart part, MobGeometry.Bone bone, Map<String, ModelPart> parts) {
        parts.put(bone.name(), part);
        for (MobGeometry.Bone child : bone.children()) {
            collect(part.getChild(child.name()), child, parts);
        }
    }

    @Override
    public void setupAnim(CustomMobRenderState state) {
        super.setupAnim(state); // back to the rest pose
        for (Map.Entry<String, float[]> e : state.pose.bones().entrySet()) {
            ModelPart p = parts.get(e.getKey());
            if (p == null) {
                continue;
            }
            float[] b = e.getValue();
            p.xRot += b[0];
            p.yRot += b[1];
            p.zRot += b[2];
            p.x += b[3];
            p.y += b[4];
            p.z += b[5];
            p.xScale *= b[6];
            p.yScale *= b[7];
            p.zScale *= b[8];
        }
    }
}
