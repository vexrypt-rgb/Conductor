package conductor.host.mixin;

import conductor.runtime.ServerHooks;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's chat message, once vanilla has accepted it and is sending it to the others (server modules). */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerChatMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"))
    private void conductor$chat(PlayerChatMessage message, CallbackInfo ci) {
        ServerHooks.chat(player, message.signedContent());
    }
}
