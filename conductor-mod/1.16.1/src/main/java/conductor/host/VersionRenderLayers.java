package conductor.host;

import conductor.contract.RenderLayer;
import conductor.host.mixin.ItemBlockRenderTypesAccessor;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/** Puts module blocks in the chunk render layer they asked for (Minecraft 1.16.1: {@code RenderType}). Client only. */
final class VersionRenderLayers {

    private VersionRenderLayers() {}

    static int apply(Map<Block, RenderLayer> layers) {
        Map<Block, RenderType> byBlock = ItemBlockRenderTypesAccessor.conductor$typeByBlock();
        layers.forEach((block, layer) -> byBlock.put(block, layer == RenderLayer.TRANSLUCENT ? RenderType.translucent() : RenderType.cutout()));
        return layers.size();
    }
}
