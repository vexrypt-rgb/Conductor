package conductor.runtime;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The block-building calls that differ in Minecraft 1.21.4. */
final class VersionBlocks {

    private VersionBlocks() {}

    static BlockBehaviour.Properties noCollision(BlockBehaviour.Properties props) {
        return props.noCollission();
    }

    static Block leaves(BlockBehaviour.Properties props) {
        return new LeavesBlock(props);
    }
}
