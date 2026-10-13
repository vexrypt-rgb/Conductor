package conductor.host.mixin;

import conductor.runtime.Hooks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.16.1: every chat line and command the player sends goes through {@code LocalPlayer.chat}. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {

    @Inject(method = "chat", at = @At("HEAD"), cancellable = true)
    private void conductor$chat(String message, CallbackInfo ci) {
        if (Hooks.outgoingChat(message)) {
            ci.cancel(); // a Conductor command: never sent to the server
        }
    }
}
