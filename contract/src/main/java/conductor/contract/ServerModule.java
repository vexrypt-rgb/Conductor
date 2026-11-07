package conductor.contract;

import java.util.List;

/**
 * A module that runs on a server: a dedicated server, or the server inside a single-player world. It sees the
 * players who are online, gets told when they join, leave, die or chat, can add {@code /} commands, and can send
 * messages and run server commands. It needs no Minecraft or loader classes, so one jar serves every supported
 * Minecraft version. Listed in {@code META-INF/services/conductor.contract.ServerModule}; one jar may also list a
 * {@link ConductorModule} and a {@link ContentModule}.
 * <p>
 * Every method is called on the server thread and must return quickly. The events of one tick are delivered at its
 * end, in this order: joins, chat, deaths, leaves, then {@link #tick}. If a method throws, Conductor logs it and stops calling this
 * module until the game restarts; its commands then answer that it is off. The server keeps running.
 * <p>
 * One instance serves every server the game runs (single-player opens a new one per world): keep per-server state
 * in fields set in {@link #started} and clear it in {@link #stopping}.
 */
public interface ServerModule {

    /**
     * The {@code /} commands this module adds, asked once when the module is first loaded. A name vanilla or another
     * module already uses is left out with a warning.
     */
    default List<ServerCommand> commands() {
        return Jdk8.listOf();
    }

    /** The server has started (its worlds exist; no player has joined yet on a dedicated server). */
    default void started(ServerControl server) {
    }

    /** Every server tick (20 per second), after the tick's events. */
    default void tick(ServerControl server) {
    }

    /** A player joined. */
    default void joined(ServerControl server, OnlinePlayer player) {
    }

    /** A player left (or was kicked). {@code player} is as they were last seen. */
    default void left(ServerControl server, OnlinePlayer player) {
    }

    /** A player died. {@code player} is as they were seen dying. */
    default void died(ServerControl server, OnlinePlayer player) {
    }

    /** A player sent a chat message (not a command). Only watched: the message has already gone to the others. */
    default void chat(ServerControl server, OnlinePlayer player, String message) {
    }

    /** The server is stopping; players may still be listed. Nothing sent now is guaranteed to arrive. */
    default void stopping(ServerControl server) {
    }
}
