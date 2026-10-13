package conductor.host;

import com.mojang.blaze3d.vertex.PoseStack;
import conductor.contract.MobDeclaration;
import conductor.host.mixin.EntityRenderersInvoker;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.animal.Pig;

import java.util.Map;

/** Draws declared mobs with their archetype's renderer, the module's texture and scale (Minecraft 1.21.4). Client only. */
final class VersionMobRenderers {

    private VersionMobRenderers() {}

    @SuppressWarnings("unchecked")
    static int apply(Map<EntityType<?>, MobDeclaration> mobs) {
        mobs.forEach((type, d) -> {
            ResourceLocation texture = ResourceLocation.parse(d.texture());
            float k = d.scale();
            switch (d.archetype()) {
                case ZOMBIE -> EntityRenderersInvoker.conductor$register((EntityType<Zombie>) type, ctx -> new ZombieRenderer(ctx) {
                    @Override
                    public ResourceLocation getTextureLocation(ZombieRenderState state) {
                        return texture;
                    }

                    @Override
                    protected void scale(ZombieRenderState state, PoseStack pose) {
                        super.scale(state, pose);
                        pose.scale(k, k, k);
                    }
                });
                case SPIDER -> EntityRenderersInvoker.conductor$register((EntityType<Spider>) type, ctx -> new SpiderRenderer<Spider>(ctx) {
                    @Override
                    public ResourceLocation getTextureLocation(LivingEntityRenderState state) {
                        return texture;
                    }

                    @Override
                    protected void scale(LivingEntityRenderState state, PoseStack pose) {
                        super.scale(state, pose);
                        pose.scale(k, k, k);
                    }
                });
                case PIG -> EntityRenderersInvoker.conductor$register((EntityType<Pig>) type, ctx -> new PigRenderer(ctx) {
                    @Override
                    public ResourceLocation getTextureLocation(PigRenderState state) {
                        return texture;
                    }

                    @Override
                    protected void scale(PigRenderState state, PoseStack pose) {
                        super.scale(state, pose);
                        pose.scale(k, k, k);
                    }
                });
            }
        });
        return mobs.size();
    }
}
