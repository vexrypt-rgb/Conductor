package conductor.host;

import com.mojang.blaze3d.vertex.PoseStack;
import conductor.contract.MobDeclaration;
import conductor.runtime.ConductorMob;
import conductor.runtime.ContentInstaller;
import conductor.runtime.CustomMobAssets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Draws a custom mob on Minecraft 1.16.5: its model, posed by its idle and walk animations, the one its server last
 * started, and its head turned to where it looks. Missing or broken files leave a model with no parts: the mob is
 * not drawn, the game runs. Client only.
 */
public class CustomMobRenderer extends MobRenderer<ConductorMob, CustomMobModel> {

    private static final Logger LOG = LogManager.getLogger("conductor");

    private final MobDeclaration declaration;
    private final CustomMobAssets.Loaded assets;
    private final ResourceLocation texture;
    private boolean poseFailed;

    public CustomMobRenderer(EntityRenderDispatcher dispatcher, MobDeclaration declaration) {
        this(dispatcher, declaration, CustomMobAssets.get(declaration, ContentInstaller.MINECRAFT));
    }

    private CustomMobRenderer(EntityRenderDispatcher dispatcher, MobDeclaration declaration, CustomMobAssets.Loaded assets) {
        super(dispatcher, new CustomMobModel(assets == null ? null : assets.geometry()),
                declaration.custom().map(c -> c.width() * 0.5f * declaration.scale()).orElse(0.5f));
        this.declaration = declaration;
        this.assets = assets;
        this.texture = new ResourceLocation(declaration.texture());
    }

    @Override
    public ResourceLocation getTextureLocation(ConductorMob mob) {
        return texture;
    }

    @Override
    public void render(ConductorMob mob, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
        pose(mob, partialTick);
        super.render(mob, yaw, partialTick, stack, buffers, light);
    }

    /** The same inputs later versions' render states carry, computed as 1.16's LivingEntityRenderer does. */
    private void pose(ConductorMob mob, float partialTick) {
        model.pose.clear();
        if (assets == null || poseFailed) {
            return;
        }
        try {
            float age = mob.tickCount + partialTick;
            float walkSpeed = Math.min(1, Mth.lerp(partialTick, mob.animationSpeedOld, mob.animationSpeed));
            float walkPosition = mob.animationPosition - mob.animationSpeed * (1 - partialTick);
            float bodyYaw = Mth.rotLerp(partialTick, mob.yBodyRotO, mob.yBodyRot);
            float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, mob.yHeadRotO, mob.yHeadRot) - bodyYaw);
            float headPitch = Mth.lerp(partialTick, mob.xRotO, mob.xRot);
            assets.animations().play(model.pose, age / 20.0, walkPosition, walkSpeed, mob.triggeredAnimation(),
                    mob.triggeredSeconds(partialTick));
            model.pose.rotate("head", (float) Math.toRadians(headPitch), (float) Math.toRadians(headYaw), 0);
        } catch (RuntimeException e) {
            poseFailed = true;
            model.pose.clear();
            LOG.error("Conductor mob {}: its animations failed; it is drawn unanimated from now on", declaration.id(), e);
        }
    }

    @Override
    protected void scale(ConductorMob mob, PoseStack pose, float partialTick) {
        super.scale(mob, pose, partialTick);
        float k = declaration.scale();
        pose.scale(k, k, k);
    }
}
