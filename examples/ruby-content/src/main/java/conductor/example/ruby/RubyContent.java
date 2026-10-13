package conductor.example.ruby;

import conductor.contract.BlockMaterial;
import conductor.contract.BlockShape;
import conductor.contract.BlockSound;
import conductor.contract.ContentDeclarations;
import conductor.contract.ContentModule;
import conductor.contract.ItemRarity;

/**
 * Rubies: an item, a food, a block and its stairs, slab, wall, pillar, glass and door, declared once for every
 * Minecraft version. Everything else is data in this
 * jar: models, textures and names ({@code assets/ruby}), the block's loot table, two recipes, the pickaxe tag, and a
 * whole dimension whose ground is made of ruby blocks ({@code data/ruby/dimension/ruby_world.json}).
 */
public final class RubyContent implements ContentModule {

    @Override
    public String namespace() {
        return "ruby";
    }

    @Override
    public void declare(ContentDeclarations content) {
        content.item("ruby").rarity(ItemRarity.UNCOMMON).tab("minecraft:ingredients");
        content.item("ruby_apple").food(6, 1.2f).tab("minecraft:food_and_drinks");
        content.block("ruby_block").strength(5, 6).sound(BlockSound.METAL).requiresTool().light(3)
                .tab("minecraft:building_blocks");
        // Blocks with states: each is vanilla's block class of that kind, wired by this jar's blockstate JSON.
        content.block("ruby_stairs").stairsOf("ruby_block").strength(5, 6).sound(BlockSound.METAL).requiresTool()
                .tab("minecraft:building_blocks");
        content.block("ruby_slab").shape(BlockShape.SLAB).strength(5, 6).sound(BlockSound.METAL).requiresTool()
                .tab("minecraft:building_blocks");
        content.block("ruby_wall").shape(BlockShape.WALL).strength(5, 6).sound(BlockSound.METAL).requiresTool()
                .tab("minecraft:building_blocks");
        content.block("ruby_pillar").shape(BlockShape.PILLAR).strength(5, 6).sound(BlockSound.METAL).requiresTool()
                .tab("minecraft:building_blocks");
        content.block("ruby_glass").shape(BlockShape.GLASS).strength(0.3f, 0.3f).sound(BlockSound.GLASS)
                .tab("minecraft:colored_blocks");
        content.block("ruby_door").shape(BlockShape.DOOR).material(BlockMaterial.WOOD).strength(3, 3)
                .sound(BlockSound.WOOD).tab("minecraft:building_blocks");
    }
}
