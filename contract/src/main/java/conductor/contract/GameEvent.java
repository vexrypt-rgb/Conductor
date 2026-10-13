package conductor.contract;

import java.util.Optional;

/**
 * Something that happened in the game, delivered to modules as topic messages on the game thread at the end of the
 * tick ({@link Topics}). Plain data only: no game objects, so the same module works on every version.
 */
public interface GameEvent {

    /** A block the server changed. */
    static final class BlockChange implements GameEvent {
        private final BlockPoint at;
        private final String block;

        public BlockChange(BlockPoint at, String block) {
            this.at = at;
            this.block = block;
        }

        public BlockPoint at() { return at; }

        public String block() { return block; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockChange)) return false;
            BlockChange other = (BlockChange) o;
            return java.util.Arrays.deepEquals(new Object[] {this.at, this.block}, new Object[] {other.at, other.block});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.at, this.block}); }

        @Override public String toString() { return "BlockChange[" + "at=" + at + ", " + "block=" + block + "]"; }
    }

    /** A chunk was loaded or unloaded on the client. */
    static final class ChunkChange implements GameEvent {
        private final int chunkX;
        private final int chunkZ;
        private final boolean loaded;

        public ChunkChange(int chunkX, int chunkZ, boolean loaded) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.loaded = loaded;
        }

        public int chunkX() { return chunkX; }

        public int chunkZ() { return chunkZ; }

        public boolean loaded() { return loaded; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkChange)) return false;
            ChunkChange other = (ChunkChange) o;
            return java.util.Arrays.deepEquals(new Object[] {this.chunkX, this.chunkZ, this.loaded}, new Object[] {other.chunkX, other.chunkZ, other.loaded});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.chunkX, this.chunkZ, this.loaded}); }

        @Override public String toString() { return "ChunkChange[" + "chunkX=" + chunkX + ", " + "chunkZ=" + chunkZ + ", " + "loaded=" + loaded + "]"; }
    }

    /** The local player died. */
    static final class PlayerDeath implements GameEvent {

        public PlayerDeath() {
        }

        @Override public boolean equals(Object o) { return o instanceof PlayerDeath; }

        @Override public int hashCode() { return 0; }

        @Override public String toString() { return "PlayerDeath[]"; }
    }

    /**
     * A chat line the client received.
     *
     * @param sender the sending player's name for player chat; empty for system messages or when not known
     */
    static final class ChatMessage implements GameEvent {
        private final String text;
        private final Optional<String> sender;

        public ChatMessage(String text, Optional<String> sender) {
            this.text = text;
            this.sender = sender;
        }

        public String text() { return text; }

        public Optional<String> sender() { return sender; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChatMessage)) return false;
            ChatMessage other = (ChatMessage) o;
            return java.util.Arrays.deepEquals(new Object[] {this.text, this.sender}, new Object[] {other.text, other.sender});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.text, this.sender}); }

        @Override public String toString() { return "ChatMessage[" + "text=" + text + ", " + "sender=" + sender + "]"; }
    }

    /** A line the player typed that starts with Conductor's command prefix (internal; modules see {@link CommandCall}). */
    static final class CommandLine implements GameEvent {
        private final String line;

        public CommandLine(String line) {
            this.line = line;
        }

        public String line() { return line; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CommandLine)) return false;
            CommandLine other = (CommandLine) o;
            return java.util.Arrays.deepEquals(new Object[] {this.line}, new Object[] {other.line});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.line}); }

        @Override public String toString() { return "CommandLine[" + "line=" + line + "]"; }
    }
}
