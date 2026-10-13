package conductor.host.mixin;

import conductor.runtime.ContentInstaller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Declared mobs' attributes: vanilla keeps them in a private immutable map, so they are answered here. */
@Mixin(DefaultAttributes.class)
abstract class DefaultAttributesMixin {

    @Inject(method = "getSupplier", at = @At("HEAD"), cancellable = true)
    private static void conductor$mobSupplier(EntityType<? extends LivingEntity> type, CallbackInfoReturnable<AttributeSupplier> cir) {
        AttributeSupplier mine = ContentInstaller.mobAttributes(type);
        if (mine != null) {
            cir.setReturnValue(mine);
        }
    }

    @Inject(method = "hasSupplier", at = @At("HEAD"), cancellable = true)
    private static void conductor$hasMobSupplier(EntityType<?> type, CallbackInfoReturnable<Boolean> cir) {
        if (ContentInstaller.isMob(type)) {
            cir.setReturnValue(true);
        }
    }
}
