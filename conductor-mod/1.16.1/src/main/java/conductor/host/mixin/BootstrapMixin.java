package conductor.host.mixin;

import conductor.runtime.ContentInstaller;
import net.minecraft.server.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.16.1 registers content when bootstrap ends: vanilla's entries are in, and nothing has read the
 * registries for a world yet. (1.16 has no registry freeze; 1.21 uses the one before freezing.)
 */
@Mixin(Bootstrap.class)
abstract class BootstrapMixin {

    @Inject(method = "bootStrap", at = @At("TAIL"))
    private static void conductor$registerContent(CallbackInfo ci) {
        ContentInstaller.afterBootstrap();
    }
}
