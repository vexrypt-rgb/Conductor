package conductor.runtime;

import conductor.contract.MobDeclaration;
import conductor.contract.RenderLayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/** Minecraft 1.16.1: content modules are not installed on this version yet; behaviour modules and overlays are. */
public final class ContentInstaller {

    private ContentInstaller() {}

    public static Map<EntityType<?>, MobDeclaration> mobs() {
        return Map.of();
    }

    public static Map<Block, RenderLayer> renderLayers() {
        return Map.of();
    }
}
