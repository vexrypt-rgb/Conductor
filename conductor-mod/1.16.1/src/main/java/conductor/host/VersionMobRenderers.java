package conductor.host;

import com.mojang.blaze3d.vertex.PoseStack;
import conductor.contract.MobDeclaration;
import conductor.runtime.ContentInstaller;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Draws declared mobs with their archetype's renderer, the module's texture and scale (Minecraft 1.16.1). 1.16 builds
 * renderers once, inside the dispatcher's constructor, so they are added from the hook there. Client only.
 */
public final class VersionMobRenderers {

    private static final Logger LOG = LogManager.getLogger("conductor");

    private VersionMobRenderers() {}

    /** At client start-up: nothing to do here on 1.16.1 (see {@link #register}). */
    static int apply(Map<EntityType<?>, MobDeclaration> mobs) {
        return 0;
    }

    /**
     * From the hook at the end of {@code EntityRenderDispatcher.registerRenderers}. Vanilla then stops start-up for any
     * type without a renderer, so a mob whose renderer can't be made gets one that draws nothing, and the game starts.
     */
    public static void register(EntityRenderDispatcher dispatcher, BiConsumer<EntityType<?>, EntityRenderer<?>> register) {
        Map<EntityType<?>, MobDeclaration> mobs = ContentInstaller.mobs();
        for (Map.Entry<EntityType<?>, MobDeclaration> e : mobs.entrySet()) {
            MobDeclaration d = e.getValue();
            EntityRenderer<?> renderer;
            try {
                renderer = create(dispatcher, d);
            } catch (RuntimeException | LinkageError ex) {
                LOG.warn("Conductor content: no renderer for {} ({}); it is not drawn", d.id(), ex.toString());
                renderer = new EntityRenderer<Entity>(dispatcher) {
                    @Override
                    public ResourceLocation getTextureLocation(Entity entity) {
                        return new ResourceLocation(d.texture());
                    }
                };
            }
            register.accept(e.getKey(), renderer);
        }
        if (!mobs.isEmpty()) {
            LOG.info("Conductor content: renderers for {} mob(s)", mobs.size());
        }
    }

    private static EntityRenderer<?> create(EntityRenderDispatcher dispatcher, MobDeclaration d) {
        ResourceLocation texture = new ResourceLocation(d.texture());
        float k = d.scale();
        switch (d.archetype()) {
            case ZOMBIE:
                return new ZombieRenderer(dispatcher) {
                    @Override
                    public ResourceLocation getTextureLocation(Zombie entity) {
                        return texture;
                    }

                    @Override
                    protected void scale(Zombie entity, PoseStack pose, float partialTick) {
                        super.scale(entity, pose, partialTick);
                        pose.scale(k, k, k);
                    }
                };
            case SPIDER:
                return new SpiderRenderer<Spider>(dispatcher) {
                    @Override
                    public ResourceLocation getTextureLocation(Spider entity) {
                        return texture;
                    }

                    @Override
                    protected void scale(Spider entity, PoseStack pose, float partialTick) {
                        super.scale(entity, pose, partialTick);
                        pose.scale(k, k, k);
                    }
                };
            case PIG:
                return new PigRenderer(dispatcher) {
                    @Override
                    public ResourceLocation getTextureLocation(Pig entity) {
                        return texture;
                    }

                    @Override
                    protected void scale(Pig entity, PoseStack pose, float partialTick) {
                        super.scale(entity, pose, partialTick);
                        pose.scale(k, k, k);
                    }
                };
            default:
                throw new IllegalArgumentException("no Minecraft 1.16.1 renderer for mob archetype " + d.archetype());
        }
    }
}
