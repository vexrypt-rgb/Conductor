package conductor.host.mixin;

import conductor.runtime.Hooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Conductor's heartbeat: the end of every client tick. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void conductor$tick(CallbackInfo ci) {
        Hooks.clientTick();
    }
}
