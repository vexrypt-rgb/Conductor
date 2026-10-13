package conductor.contract;

import java.util.List;
import java.util.Optional;

/**
 * The running server, as a {@link ServerModule} sees it during a call. Server thread only: do not keep it for use
 * from another thread. Players are named by their in-game name (case-insensitive).
 */
public interface ServerControl {

    /** The Minecraft version, e.g. {@code 1.21.11}. */
    String minecraftVersion();

    /** True for a dedicated server, false for the server of a single-player (or LAN) world. */
    boolean dedicated();

    /** Ticks since this server started. */
    long tick();

    /** The players online now, in join order. */
    List<OnlinePlayer> players();

    Optional<OnlinePlayer> player(String name);

    /** Send {@code message} to one player, as a system message. False if they are not online. */
    boolean tell(String player, String message);

    /** Send {@code message} to every player online, as a system message (also written to the server log). */
    void broadcast(String message);

    /**
     * Run a server command as the server console, with full permission, for example {@code "time set day"} (a
     * leading {@code /} is allowed). Its output goes to the server log. Vanilla command syntax differs between
     * Minecraft versions; a module that runs on several should keep to commands that read the same on all of them.
     */
    void runCommand(String command);

    /**
     * The block at a position, e.g. {@code minecraft:stone}. Empty when the dimension does not exist or that chunk is
     * not loaded (this never loads a chunk).
     *
     * @param dimension e.g. {@code minecraft:overworld}
     */
    Optional<String> blockAt(String dimension, int x, int y, int z);

    /** Write to the server log, prefixed with this module's id. */
    void log(String message);
}
