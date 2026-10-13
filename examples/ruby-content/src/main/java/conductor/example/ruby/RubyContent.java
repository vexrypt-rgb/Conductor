package conductor.example.ruby;

import conductor.contract.BlockMaterial;
import conductor.contract.BlockShape;
import conductor.contract.BlockSound;
import conductor.contract.BossBar;
import conductor.contract.ContentDeclarations;
import conductor.contract.ContentModule;
import conductor.contract.ItemRarity;
import conductor.contract.MobArchetype;
import conductor.contract.MobGoal;

/**
 * Rubies: an item, a food, a block and its stairs, slab, wall, pillar, glass and door, three mobs and a boss, declared
 * once for every Minecraft version. Everything else is data in this
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
        // Mobs on vanilla archetypes: their AI and body, this jar's texture, name and drops (loot_table/entities/...).
        content.mob("ruby_zombie").like(MobArchetype.ZOMBIE).health(30).attackDamage(5).scale(1.2f);
        content.mob("ruby_spider").like(MobArchetype.SPIDER).health(12).scale(0.7f);
        content.mob("ruby_pig").like(MobArchetype.PIG).speed(0.3);
        // A fully custom boss: its own model and animations (assets/ruby/conductor/...), vanilla goals to walk and
        // fight, and RubyGolemBehaviour for the rest: a ground slam, fireballs, and at half health it calls ruby zombies.
        content.mob("ruby_golem").model("ruby_golem").animations("ruby_golem").size(1.4f, 2.7f)
                .health(200).attackDamage(12).speed(0.25).armor(8).knockbackResistance(1).followRange(32).fireImmune()
                .boss(BossBar.Color.PINK, BossBar.Style.NOTCHED_10)
                .goals(MobGoal.swim(), MobGoal.meleeAttack(1.0), MobGoal.wander(0.6), MobGoal.lookAtPlayers(16),
                        MobGoal.lookAround(), MobGoal.retaliate(), MobGoal.targetPlayers())
                .sounds(null, "minecraft:entity.iron_golem.hurt", "minecraft:entity.iron_golem.death")
                .stepSound("minecraft:entity.iron_golem.step")
                .behaviour(new RubyGolemBehaviour());
    }
}
