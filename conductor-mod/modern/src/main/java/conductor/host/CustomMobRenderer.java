package conductor.host;

import com.mojang.blaze3d.vertex.PoseStack;
import conductor.contract.MobDeclaration;
import conductor.runtime.ConductorMob;
import conductor.runtime.CustomMobAssets;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Draws a custom mob: its model, posed by its idle and walk animations, the one its server last started, and its head
 * turned to where it looks. Missing or broken files leave a model with no parts: the mob is not drawn, the game runs.
 * The texture id's class differs by version, so each version's {@code VersionMobRenderers} supplies it. Client only.
 */
public abstract class CustomMobRenderer extends MobRenderer<ConductorMob, CustomMobRenderState, CustomMobModel> {

    private static final Logger LOG = LogManager.getLogger("conductor");

    private final MobDeclaration declaration;
    private final CustomMobAssets.Loaded assets;
    private boolean poseFailed;

    protected CustomMobRenderer(EntityRendererProvider.Context context, MobDeclaration declaration, String minecraft) {
        this(context, declaration, CustomMobAssets.get(declaration, minecraft));
    }

    private CustomMobRenderer(EntityRendererProvider.Context context, MobDeclaration declaration, CustomMobAssets.Loaded assets) {
        super(context, CustomMobModel.of(assets == null ? null : assets.geometry()),
                declaration.custom().map(c -> c.width() * 0.5f * declaration.scale()).orElse(0.5f));
        this.declaration = declaration;
        this.assets = assets;
    }

    @Override
    public CustomMobRenderState createRenderState() {
        return new CustomMobRenderState();
    }

    @Override
    public void extractRenderState(ConductorMob mob, CustomMobRenderState state, float partialTick) {
        super.extractRenderState(mob, state, partialTick);
        state.pose.clear();
        if (assets == null || poseFailed) {
            return;
        }
        try {
            assets.animations().play(state.pose, state.ageInTicks / 20.0, state.walkAnimationPos, state.walkAnimationSpeed,
                    mob.triggeredAnimation(), mob.triggeredSeconds(partialTick));
            state.pose.rotate("head", (float) Math.toRadians(state.xRot), (float) Math.toRadians(state.yRot), 0);
        } catch (RuntimeException e) {
            poseFailed = true;
            state.pose.clear();
            LOG.error("Conductor mob {}: its animations failed; it is drawn unanimated from now on", declaration.id(), e);
        }
    }

    @Override
    protected void scale(CustomMobRenderState state, PoseStack pose) {
        super.scale(state, pose);
        float k = declaration.scale();
        pose.scale(k, k, k);
    }
}
