package conductor.host.mixin;

import conductor.runtime.ServerHooks;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's chat message on 1.16.1, as vanilla sends it to the others (server modules). 1.16.1 has no signed chat:
 * the hook is the one broadcast in {@code handleChat}, which commands never reach.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerChatMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType;Ljava/util/UUID;)V"))
    private void conductor$chat(ServerboundChatPacket packet, CallbackInfo ci) {
        ServerHooks.chat(player, StringUtils.normalizeSpace(packet.getMessage()));
    }
}
