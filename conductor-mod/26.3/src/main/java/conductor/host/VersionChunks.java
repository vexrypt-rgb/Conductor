package conductor.host;

import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

/** Chunk coordinates from chunk packets (Minecraft 26.3: the packet and {@code ChunkPos} are records). Client only. */
public final class VersionChunks {

    private VersionChunks() {}

    /** {x, z} of a chunk the server sent. */
    public static int[] loaded(ClientboundLevelChunkWithLightPacket packet) {
        return new int[]{packet.x(), packet.z()};
    }

    /** {x, z} of a chunk the server unloaded. */
    public static int[] forgotten(ClientboundForgetLevelChunkPacket packet) {
        return new int[]{packet.pos().x(), packet.pos().z()};
    }
}
