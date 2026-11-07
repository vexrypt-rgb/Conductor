package conductor.host.mixin;

import conductor.host.VersionMobRenderers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Declared mobs' renderers (Minecraft 1.16.5). Vanilla builds every renderer in this method and then fails start-up
 * for any entity type without one, so they are added here, before that check. Client only.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {

    @Shadow
    private <T extends Entity> void register(EntityType<T> type, EntityRenderer<? super T> renderer) {
        throw new AssertionError();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "registerRenderers", at = @At("TAIL"))
    private void conductor$mobRenderers(ItemRenderer items, ReloadableResourceManager resources, CallbackInfo ci) {
        VersionMobRenderers.register((EntityRenderDispatcher) (Object) this, (type, renderer) -> register((EntityType) type, (EntityRenderer) renderer));
    }
}
