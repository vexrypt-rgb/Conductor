package conductor.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** The player calls that differ in Minecraft 26.2. */
final class VersionPlayer {

    private VersionPlayer() {}

    /**
     * The arm swing that follows an attack, as vanilla's {@code Minecraft.startAttack} does it in 26.2: the local swing
     * also sends {@code ServerboundSwingPacket} to the server.
     */
    static void swingAfterAttack(LocalPlayer p) {
        p.swing(InteractionHand.MAIN_HAND);
    }

    /** A message only this player sees (the chat moved to {@code Gui.hud} in 26.x). */
    static void showMessage(Minecraft mc, Component text) {
        mc.gui.hud.getChat().addClientSystemMessage(text);
    }
}
