package conductor.host;

import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

/** Chunk coordinates from chunk packets (Minecraft 26.2: the load packet has getters; {@code ChunkPos} is a record). Client only. */
public final class VersionChunks {

    private VersionChunks() {}

    /** {x, z} of a chunk the server sent. */
    public static int[] loaded(ClientboundLevelChunkWithLightPacket packet) {
        return new int[]{packet.getX(), packet.getZ()};
    }

    /** {x, z} of a chunk the server unloaded. */
    public static int[] forgotten(ClientboundForgetLevelChunkPacket packet) {
        return new int[]{packet.pos().x(), packet.pos().z()};
    }
}
