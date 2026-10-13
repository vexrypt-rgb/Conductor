package conductor.contract;

import java.util.Optional;

/**
 * Something that happened in the game, delivered to modules as topic messages on the game thread at the end of the
 * tick ({@link Topics}). Plain data only: no game objects, so the same module works on every version.
 */
public sealed interface GameEvent permits GameEvent.BlockChange, GameEvent.ChunkChange, GameEvent.PlayerDeath,
        GameEvent.ChatMessage, GameEvent.CommandLine {

    /** A block the server changed. */
    record BlockChange(BlockPoint at, String block) implements GameEvent {}

    /** A chunk was loaded or unloaded on the client. */
    record ChunkChange(int chunkX, int chunkZ, boolean loaded) implements GameEvent {}

    /** The local player died. */
    record PlayerDeath() implements GameEvent {}

    /**
     * A chat line the client received.
     *
     * @param sender the sending player's name for player chat; empty for system messages or when not known
     */
    record ChatMessage(String text, Optional<String> sender) implements GameEvent {}

    /** A line the player typed that starts with Conductor's command prefix (internal; modules see {@link CommandCall}). */
    record CommandLine(String line) implements GameEvent {}
}
