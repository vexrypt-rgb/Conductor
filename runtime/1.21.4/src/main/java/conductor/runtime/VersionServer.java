package conductor.runtime;

import net.minecraft.commands.CommandSourceStack;

/** Minecraft 1.21.4's operator check for server module commands. */
final class VersionServer {

    private VersionServer() {}

    /** May use operator commands: permission level 2. */
    static boolean operator(CommandSourceStack source) {
        return source.hasPermission(2);
    }
}
