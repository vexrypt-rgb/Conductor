package conductor.host.mixin;

import conductor.runtime.Hooks;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Conductor's heartbeat on the client: the end of every client tick. */
@Mixin(MinecraftClient.class)
abstract class ClientTickMixin {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void conductor$tick(CallbackInfo ci) {
        Hooks.clientTick();
    }
}
