package conductor.harness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.4 specifics used by the harness. */
final class Compat {

    private Compat() {}

    static String dimension(Level level) {
        return level.dimension().location().toString();
    }

    static void connect(Minecraft mc, String server) {
        ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(server),
                new ServerData("conductor-harness", server, ServerData.Type.OTHER), false, null);
    }
}
