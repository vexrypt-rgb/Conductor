package conductor.runtime;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Function;

/**
 * The block-building calls that differ in Minecraft 26.2. {@code Items}' register methods are private here, so the two
 * Conductor uses are written out as vanilla's are (same calls, same order).
 */
final class VersionBlocks {

    /** Broken when a piston pushes it (doors, buttons, leaves); {@code DESTROY} in 26.2 (1.21 has the same value). */
    static final PushReaction POPPED = PushReaction.DESTROY;

    private VersionBlocks() {}

    static BlockBehaviour.Properties noCollision(BlockBehaviour.Properties props) {
        return props.noCollision();
    }

    static Block leaves(BlockBehaviour.Properties props) {
        return new TintedParticleLeavesBlock(0.01f, props);
    }

    /** Neither suffocates nor blocks the view (glass, leaves, small models). */
    static BlockBehaviour.Properties seeThrough(BlockBehaviour.Properties props) {
        return props.isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    /** Registers the block's item under the block's id, as vanilla's {@code Items.registerBlock} does. */
    static Item blockItem(ResourceKey<Block> key, Block block, boolean door) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, key.identifier());
        Item.Properties props = new Item.Properties().useBlockDescriptionPrefix().requiredFeatures(block.requiredFeatures());
        return register(itemKey, p -> door ? new DoubleHighBlockItem(block, p) : new BlockItem(block, p), props);
    }

    static Item item(ResourceKey<Item> key, Item.Properties props) {
        return register(key, Item::new, props);
    }

    /** Vanilla's {@code Items.registerItem(ResourceKey, Function, Properties)}. */
    private static Item register(ResourceKey<Item> key, Function<Item.Properties, Item> factory, Item.Properties props) {
        Item item = factory.apply(props.setId(key));
        if (item instanceof BlockItem blockItem) {
            blockItem.registerBlocks(Item.BY_BLOCK, item);
        }
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
