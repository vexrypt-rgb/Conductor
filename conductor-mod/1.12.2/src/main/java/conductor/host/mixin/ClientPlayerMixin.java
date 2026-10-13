package conductor.host.mixin;

import conductor.runtime.Hooks;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.12.2: every chat line and command the player sends goes through {@code sendChatMessage}. */
@Mixin(ClientPlayerEntity.class)
abstract class ClientPlayerMixin {

    @Inject(method = "sendChatMessage(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void conductor$outgoingChat(String message, CallbackInfo ci) {
        if (Hooks.outgoingChat(message)) {
            ci.cancel(); // a Conductor "+" command: it is not sent to the server
        }
    }
}
