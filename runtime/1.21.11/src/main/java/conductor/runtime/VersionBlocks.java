package conductor.runtime;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/** The block-building calls that differ in Minecraft 1.21.11. */
final class VersionBlocks {

    /** Broken when a piston pushes it (doors, buttons, leaves). */
    static final PushReaction POPPED = PushReaction.DESTROY;

    private VersionBlocks() {}

    /** Neither suffocates nor blocks the view (glass, leaves, small models). */
    static BlockBehaviour.Properties seeThrough(BlockBehaviour.Properties props) {
        return props.isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    /** Registers the block's item under the block's id. */
    static Item blockItem(ResourceKey<Block> key, Block block, boolean door) {
        return door ? Items.registerBlock(block, DoubleHighBlockItem::new) : Items.registerBlock(block);
    }

    static Item item(ResourceKey<Item> key, Item.Properties props) {
        return Items.registerItem(key, Item::new, props);
    }

    static BlockBehaviour.Properties noCollision(BlockBehaviour.Properties props) {
        return props.noCollision();
    }

    static Block leaves(BlockBehaviour.Properties props) {
        return new TintedParticleLeavesBlock(0.01f, props);
    }
}
