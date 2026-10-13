package conductor.host.mixin;

import conductor.runtime.Hooks;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Conductor's game events on 1.16.5: the same events as the 1.21.x hook, from this version's packets. Handlers are
 * hooked at RETURN: vanilla first runs each on the network thread only to re-schedule it, so RETURN is reached once
 * on the game thread.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {

    private static boolean conductor$gameThread() {
        return Minecraft.getInstance().isSameThread();
    }

    private static String conductor$id(BlockState state) {
        return String.valueOf(Registry.BLOCK.getKey(state.getBlock()));
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
            // 1.16.5 reads a section's updates through runUpdates (no list of BlockUpdate objects as in 1.16.1)
            packet.runUpdates((p, state) -> Hooks.blockChanged(p.getX(), p.getY(), p.getZ(), conductor$id(state)));
        }
    }

    @Inject(method = "handleLevelChunk", at = @At("RETURN"))
    private void conductor$chunkLoaded(ClientboundLevelChunkPacket packet, CallbackInfo ci) {
        // 1.16.5 also sends partial chunk updates in this packet; only a full chunk is a newly loaded one
        if (conductor$gameThread() && packet.isFullChunk()) {
            Hooks.chunkLoaded(packet.getX(), packet.getZ(), true);
        }
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("RETURN"))
    private void conductor$chunkForgotten(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        if (conductor$gameThread()) {
            Hooks.chunkLoaded(packet.getX(), packet.getZ(), false);
        }
    }

    @Inject(method = "handleChat", at = @At("RETURN"))
    private void conductor$chat(ClientboundChatPacket packet, CallbackInfo ci) {
        if (!conductor$gameThread() || packet.getType() == ChatType.GAME_INFO) {
            return; // GAME_INFO is the action bar, like 1.21.x's overlay messages
        }
        Component message = packet.getMessage();
        UUID sender = packet.getSender();
        // a player's line is "chat.type.text" with (name, text): hand over the text alone, as 1.21.x does
        if (packet.getType() == ChatType.CHAT && sender != null && !Util.NIL_UUID.equals(sender)
                && message instanceof TranslatableComponent t && "chat.type.text".equals(t.getKey()) && t.getArgs().length == 2) {
            Object text = t.getArgs()[1];
            Hooks.chatReceived(text instanceof Component c ? c.getString() : String.valueOf(text), sender);
        } else {
            Hooks.chatReceived(message.getString(), null);
        }
    }

    @Inject(method = "handlePlayerCombat", at = @At("RETURN"))
    private void conductor$death(ClientboundPlayerCombatPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (conductor$gameThread() && mc.player != null && packet.event == ClientboundPlayerCombatPacket.Event.ENTITY_DIED
                && packet.playerId == mc.player.getId()) {
            Hooks.playerDied();
        }
    }
}
