package conductor.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** The player calls that differ in Minecraft 1.21.4. */
final class VersionPlayer {

    private VersionPlayer() {}

    /** The arm swing (and its packet) that follows an attack. */
    static void swingAfterAttack(LocalPlayer p) {
        p.swing(InteractionHand.MAIN_HAND);
    }

    /** A message only this player sees. */
    static void showMessage(Minecraft mc, Component text) {
        mc.gui.getChat().addMessage(text);
    }
}
