package conductor.host.mixin;

import conductor.runtime.Hooks;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Conductor's game events on 1.8.9, from this version's packets. Handlers are hooked at RETURN: vanilla first runs
 * each on the network thread only to re-schedule it, so RETURN is reached once on the game thread.
 */
@Mixin(ClientPlayNetworkHandler.class)
abstract class ClientPacketMixin {

    private static boolean conductor$gameThread() {
        return MinecraftClient.getInstance().isOnThread();
    }

    @Inject(method = "onBlockUpdate", at = @At("RETURN"))
    private void conductor$blockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        if (conductor$gameThread()) {
            BlockPos p = packet.getPos();
            Hooks.blockChanged(p.getX(), p.getY(), p.getZ(), String.valueOf(Block.REGISTRY.getIdentifier(packet.getState().getBlock())));
        }
    }

    @Inject(method = "onChunkData", at = @At("RETURN"))
    private void conductor$chunkLoaded(ChunkDataS2CPacket packet, CallbackInfo ci) {
        if (conductor$gameThread()) {
            // 1.8.9 sends unloads as chunk data too: shouldLoad() is false for an unload
            Hooks.chunkLoaded(packet.getChunkX(), packet.getChunkZ(), packet.shouldLoad());
        }
    }

    @Inject(method = "onChatMessage", at = @At("RETURN"))
    private void conductor$chat(ChatMessageS2CPacket packet, CallbackInfo ci) {
        if (conductor$gameThread()) {
            // 1.8.9 chat has no sender identity: the line is handed over as it is shown
            Hooks.chatReceived(packet.getMessage().asUnformattedString(), null);
        }
    }

    @Inject(method = "onEntityStatus", at = @At("RETURN"))
    private void conductor$death(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (conductor$gameThread() && packet.getStatus() == 3 && mc.player != null
                && packet.getEntity(mc.world) == mc.player) {
            Hooks.playerDied(); // status 3: a living entity died
        }
    }
}
