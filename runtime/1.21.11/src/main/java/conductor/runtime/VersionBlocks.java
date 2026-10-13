package conductor.runtime;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The block-building calls that differ in Minecraft 1.21.11. */
final class VersionBlocks {

    private VersionBlocks() {}

    static BlockBehaviour.Properties noCollision(BlockBehaviour.Properties props) {
        return props.noCollision();
    }

    static Block leaves(BlockBehaviour.Properties props) {
        return new TintedParticleLeavesBlock(0.01f, props);
    }
}
