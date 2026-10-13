package conductor.host;

import conductor.contract.RenderLayer;
import conductor.host.mixin.ItemBlockRenderTypesAccessor;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/** Puts module blocks in the chunk render layer they asked for (Minecraft 1.21.11: {@code ChunkSectionLayer}). Client only. */
final class VersionRenderLayers {

    private VersionRenderLayers() {}

    static int apply(Map<Block, RenderLayer> layers) {
        Map<Block, ChunkSectionLayer> byBlock = ItemBlockRenderTypesAccessor.conductor$typeByBlock();
        layers.forEach((block, layer) -> byBlock.put(block, layer == RenderLayer.TRANSLUCENT ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.CUTOUT));
        return layers.size();
    }
}
