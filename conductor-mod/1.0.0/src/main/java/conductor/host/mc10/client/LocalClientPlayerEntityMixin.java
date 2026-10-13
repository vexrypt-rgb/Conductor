package conductor.host.mc10.client;

import conductor.runtime.Hooks;
import net.minecraft.client.entity.mob.player.LocalClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.0.0: every chat line and command the player sends on a server goes through here (single-player has no chat). */
@Mixin(LocalClientPlayerEntity.class)
abstract class LocalClientPlayerEntityMixin {

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void conductor$chat(String message, CallbackInfo ci) {
        if (Hooks.outgoingChat(message)) {
            ci.cancel(); // a Conductor command: never sent to the server
        }
    }
}
