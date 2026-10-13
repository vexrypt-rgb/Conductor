package conductor.host.mixin;

import conductor.runtime.ServerHooks;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server modules' chat events: a player's message as the server receives it. */
@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void conductor$chat(ChatMessageC2SPacket packet, CallbackInfo ci) {
        ServerHooks.chat(player, packet.getChatMessage());
    }
}
