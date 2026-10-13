package conductor.host.mixin;

import conductor.host.VersionChunks;
import conductor.runtime.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Conductor's game events and chat commands. Handlers are hooked at RETURN: vanilla first runs each handler on the
 * network thread only to re-schedule it (it throws there), so RETURN is reached once, on the game thread.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {

    private static boolean conductor$gameThread() {
        return Minecraft.getInstance().isSameThread();
    }

    private static String conductor$id(BlockState state) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void conductor$sendChat(String message, CallbackInfo ci) {
        if (Hooks.outgoingChat(message)) {
            ci.cancel(); // a Conductor command: never sent to the server
        }
    }

    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void conductor$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (conductor$gameThread()) {
            BlockPos p = packet.getPos();
            Hooks.blockChanged(p.getX(), p.getY(), p.getZ(), conductor$id(packet.getBlockState()));
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void conductor$sectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        if (conductor$gameThread()) {
            packet.runUpdates((p, state) -> Hooks.blockChanged(p.getX(), p.getY(), p.getZ(), conductor$id(state)));
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void conductor$chunkLoaded(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (conductor$gameThread()) {
            int[] c = VersionChunks.loaded(packet);
            Hooks.chunkLoaded(c[0], c[1], true);
        }
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("RETURN"))
    private void conductor$chunkForgotten(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        if (conductor$gameThread()) {
            int[] c = VersionChunks.forgotten(packet);
            Hooks.chunkLoaded(c[0], c[1], false);
        }
    }

    @Inject(method = "handleSystemChat", at = @At("RETURN"))
    private void conductor$systemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (conductor$gameThread() && !packet.overlay()) {
            Hooks.chatReceived(packet.content().getString(), null);
        }
    }

    @Inject(method = "handlePlayerChat", at = @At("RETURN"))
    private void conductor$playerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (conductor$gameThread()) {
            Hooks.chatReceived(packet.body().content(), packet.sender());
        }
    }

    @Inject(method = "handlePlayerCombatKill", at = @At("RETURN"))
    private void conductor$death(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (conductor$gameThread() && mc.player != null && packet.playerId() == mc.player.getId()) {
            Hooks.playerDied();
        }
    }
}
