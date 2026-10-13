package conductor.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;

/** The player calls that differ in Minecraft 26.3. */
final class VersionPlayer {

    private VersionPlayer() {}

    /**
     * The arm swing that follows an attack. 26.3 swings with the held item's attack animation and sends the swing as
     * its own packet, as vanilla's {@code Minecraft.startAttack} does.
     */
    static void swingAfterAttack(LocalPlayer p) {
        p.swing(InteractionHand.MAIN_HAND, p.getItemInHand(InteractionHand.MAIN_HAND).getAttackAnimation(), false);
        p.connection.send(ServerboundPunchPacket.INSTANCE);
    }

    /** A message only this player sees (the chat moved to {@code Gui.hud} in 26.x). */
    static void showMessage(Minecraft mc, Component text) {
        mc.gui.hud.getChat().addClientSystemMessage(text);
    }
}
