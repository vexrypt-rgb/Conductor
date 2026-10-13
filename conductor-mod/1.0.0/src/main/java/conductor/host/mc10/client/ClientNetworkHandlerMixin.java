package conductor.host.mc10.client;

import conductor.runtime.Hooks;
import conductor.runtime.LegacyEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientNetworkHandler;
import net.minecraft.client.world.MultiplayerWorld;
import net.minecraft.network.packet.BlockUpdatePacket;
import net.minecraft.network.packet.BlocksUpdatePacket;
import net.minecraft.network.packet.ChatMessagePacket;
import net.minecraft.network.packet.LoadWorldChunkPacket;
import net.minecraft.network.packet.PlayerHealthPacket;
import net.minecraft.network.packet.WorldChunkPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Conductor's game events on 1.0.0, from this version's packets. 1.0 handles packets in the client tick, on the game
 * thread, so each handler is hooked once, at RETURN (when the world has the change).
 */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {

    /**
     * A region update up to this many blocks is reported block by block; a bigger one as its chunks reloaded. 1.0 sends
     * the box around 10 or more changes in a chunk, and a fresh world sends hundreds of them (288 in one live join,
     * 170 000 blocks at the old limit of 4096, which filled the event queue).
     */
    private static final int MAX_REGION_BLOCKS = 256;

    @Shadow
    private Minecraft minecraft;

    @Shadow
    private MultiplayerWorld world;

    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void conductor$blockUpdate(BlockUpdatePacket packet, CallbackInfo ci) {
        LegacyEvents.blockChanged(packet.x, packet.y, packet.z, packet.block, packet.metadata);
    }

    @Inject(method = "handleBlocksUpdate", at = @At("RETURN"))
    private void conductor$blocksUpdate(BlocksUpdatePacket packet, CallbackInfo ci) {
        for (int i = 0; i < packet.blockChangeCount; i++) {
            int pos = packet.positions[i];
            LegacyEvents.blockChanged(packet.chunkX * 16 + (pos >> 12 & 15), pos & 255, packet.chunkZ * 16 + (pos >> 8 & 15),
                    packet.blockIds[i] & 255, packet.blockMetadata[i] & 15);
        }
    }

    /** A whole chunk column (a newly loaded chunk), or a region of one with many changes at once. */
    @Inject(method = "handleWorldChunk", at = @At("RETURN"))
    private void conductor$worldChunk(WorldChunkPacket packet, CallbackInfo ci) {
        boolean column = packet.sizeX == 16 && packet.sizeZ == 16 && packet.sizeY == 128 && packet.y == 0
                && (packet.x & 15) == 0 && (packet.z & 15) == 0;
        if (column) {
            Hooks.chunkLoaded(packet.x >> 4, packet.z >> 4, true);
        } else if (packet.sizeX * packet.sizeY * packet.sizeZ <= MAX_REGION_BLOCKS && world != null) {
            for (int x = packet.x; x < packet.x + packet.sizeX; x++) {
                for (int z = packet.z; z < packet.z + packet.sizeZ; z++) {
                    for (int y = packet.y; y < packet.y + packet.sizeY; y++) {
                        LegacyEvents.blockChanged(x, y, z, world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
                    }
                }
            }
        } else {
            for (int cx = packet.x >> 4; cx <= (packet.x + packet.sizeX - 1) >> 4; cx++) {
                for (int cz = packet.z >> 4; cz <= (packet.z + packet.sizeZ - 1) >> 4; cz++) {
                    Hooks.chunkLoaded(cx, cz, true);
                }
            }
        }
    }

    @Inject(method = "handleLoadWorldChunk", at = @At("RETURN"))
    private void conductor$chunkForgotten(LoadWorldChunkPacket packet, CallbackInfo ci) {
        if (!packet.load) {
            Hooks.chunkLoaded(packet.chunkX, packet.chunkZ, false);
        }
    }

    @Inject(method = "handleChatMessage", at = @At("RETURN"))
    private void conductor$chat(ChatMessagePacket packet, CallbackInfo ci) {
        LegacyEvents.chat(packet.message);
    }

    /** 1.0 has no death packet: the player died when the health the server sends drops to 0. */
    @Inject(method = "handlePlayerHealth", at = @At("HEAD"))
    private void conductor$health(PlayerHealthPacket packet, CallbackInfo ci) {
        if (minecraft.player != null && minecraft.player.getHealth() > 0 && packet.health <= 0) {
            Hooks.playerDied();
        }
    }
}
