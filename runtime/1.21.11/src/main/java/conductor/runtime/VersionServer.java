package conductor.runtime;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/** Minecraft 1.21.11's operator check for server module commands. */
final class VersionServer {

    private VersionServer() {}

    /** May use operator commands: the game-master permission (permission sets replaced levels in 1.21.11). */
    static boolean operator(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
