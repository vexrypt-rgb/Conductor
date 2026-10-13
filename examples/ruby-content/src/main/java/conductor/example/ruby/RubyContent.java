package conductor.example.ruby;

import conductor.contract.BlockSound;
import conductor.contract.ContentDeclarations;
import conductor.contract.ContentModule;
import conductor.contract.ItemRarity;

/**
 * Rubies: an item, a food and a block, declared once for every Minecraft version. Everything else is data in this
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
    }
}
