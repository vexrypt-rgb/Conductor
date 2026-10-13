package conductor.host;

import com.mojang.blaze3d.vertex.PoseStack;
import conductor.contract.MobDeclaration;
import conductor.host.mixin.EntityRenderersInvoker;
import conductor.runtime.ConductorMob;
import conductor.runtime.VersionIds;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.animal.pig.Pig;

import java.util.Map;

/** Draws declared mobs with their archetype's renderer (custom mobs with {@link CustomMobRenderer}), the module's texture and scale (Minecraft 1.21.11). Client only. */
final class VersionMobRenderers {

    private VersionMobRenderers() {}

    @SuppressWarnings("unchecked")
    static int apply(Map<EntityType<?>, MobDeclaration> mobs) {
        mobs.forEach((type, d) -> {
            Identifier texture = Identifier.parse(d.texture());
            float k = d.scale();
            switch (d.archetype()) {
                case ZOMBIE -> EntityRenderersInvoker.conductor$register((EntityType<Zombie>) type, ctx -> new ZombieRenderer(ctx) {
                    @Override
                    public Identifier getTextureLocation(ZombieRenderState state) {
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
                    public Identifier getTextureLocation(LivingEntityRenderState state) {
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
                    public Identifier getTextureLocation(PigRenderState state) {
                        return texture;
                    }

                    @Override
                    protected void scale(PigRenderState state, PoseStack pose) {
                        super.scale(state, pose);
                        pose.scale(k, k, k);
                    }
                });
                case CUSTOM -> EntityRenderersInvoker.conductor$register((EntityType<ConductorMob>) type,
                        ctx -> new CustomMobRenderer(ctx, d, VersionIds.MINECRAFT) {
                            @Override
                            public Identifier getTextureLocation(CustomMobRenderState state) {
                                return texture;
                            }
                        });
            }
        });
        return mobs.size();
    }
}
