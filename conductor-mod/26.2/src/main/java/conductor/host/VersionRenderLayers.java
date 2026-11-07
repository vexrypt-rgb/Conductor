package conductor.host;

import conductor.contract.RenderLayer;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Minecraft 26.2 has no block-to-layer table: each face goes in the chunk layer its texture's pixels call for
 * ({@code ChunkSectionLayer.byTransparency}): cutout when the texture has fully transparent pixels, translucent when it
 * has partly transparent ones. Module textures already carry that, so there is nothing to register. Client only.
 */
final class VersionRenderLayers {

    private VersionRenderLayers() {}

    static int apply(Map<Block, RenderLayer> layers) {
        return layers.size();
    }
}
