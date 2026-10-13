package conductor.host.mixin;

import conductor.runtime.ContentInstaller;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers module content immediately before the built-in registries freeze. It runs wherever the freeze happens:
 * at bootstrap in vanilla, or later when a loader library delays it, so it never competes for the same call.
 */
@Mixin(BuiltInRegistries.class)
abstract class BuiltInRegistriesMixin {

    @Inject(method = "freeze", at = @At("HEAD"))
    private static void conductor$beforeFreeze(CallbackInfo ci) {
        ContentInstaller.beforeRegistryFreeze();
    }
}
