package conductor.host.mc10.server;

import conductor.runtime.ServerHooks;
import net.minecraft.command.source.CommandSource;
import net.minecraft.network.packet.ChatMessagePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.mob.player.ServerPlayerEntity;
import net.minecraft.server.network.handler.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's {@code /command} (module commands first; vanilla gets what no module claims) and chat line (passed on as
 * vanilla sends it to everyone, after its own length and character checks).
 */
@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    private MinecraftServer server;

    @Shadow
    private ServerPlayerEntity player;

    @Inject(method = "runCommand", at = @At("HEAD"), cancellable = true)
    private void conductor$command(String line, CallbackInfo ci) {
        if (line.startsWith("/") && ServerHooks.command(server, line.substring(1), player, (CommandSource) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChatMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void conductor$chat(ChatMessagePacket packet, CallbackInfo ci) {
        ServerHooks.chat(player, packet.message.trim());
    }
}
