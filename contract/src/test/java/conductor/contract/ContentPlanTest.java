package conductor.contract;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Content declarations: checking, whole-module skipping, and the fixed registration order. */
class ContentPlanTest {

    static ContentModule module(String ns, java.util.function.Consumer<ContentDeclarations> body) {
        return new ContentModule() {
            public String namespace() { return ns; }
            public void declare(ContentDeclarations c) { body.accept(c); }
        };
    }

    private static final ContentModule RUBY = module("ruby", c -> {
        c.item("ruby").rarity(ItemRarity.UNCOMMON).tab("minecraft:ingredients");
        c.item("ruby_apple").food(6, 1.2f);
        c.item("ruby_pick").durability(250).maxStack(16);
        c.block("ruby_block").strength(5, 6).sound(BlockSound.METAL).requiresTool().light(3).tab("minecraft:building_blocks");
        c.block("ruby_ore").noItem();
    });

    @Test
    void declarationsBecomeNamespacedRecordsWithDefaults() {
        ContentPlan plan = ContentPlan.of(Map.of("ruby-content", RUBY));
        assertEquals(List.of(), plan.problems());
        ContentPlan.Entry e = plan.entries().get(0);
        assertEquals(List.of("ruby:ruby", "ruby:ruby_apple", "ruby:ruby_pick"), e.items().stream().map(ItemDeclaration::id).toList());
        assertEquals(Optional.of("minecraft:ingredients"), e.items().get(0).tab());
        assertEquals(64, e.items().get(0).maxStack());
        assertEquals(Optional.of(new ItemDeclaration.Food(6, 1.2f, false)), e.items().get(1).food());
        assertEquals(1, e.items().get(2).maxStack(), "an item with durability stacks to 1");
        BlockDeclaration block = e.blocks().get(0);
        assertEquals(new BlockDeclaration("ruby:ruby_block", 5, 6, 3, BlockSound.METAL, true, true, Optional.of("minecraft:building_blocks")), block);
        assertTrue(!e.blocks().get(1).withItem());
        assertEquals(4, plan.itemCount(), "three items plus one block item");
        assertEquals(2, plan.blockCount());
    }

    @Test
    void anyProblemSkipsTheWholeModule() {
        ContentPlan plan = ContentPlan.of(Map.of("bad", module("bad", c -> {
            c.item("fine");
            c.item("Not Valid");
            c.block("lamp").light(16);
        })));
        assertTrue(plan.entries().isEmpty());
        assertEquals(2, plan.problems().size(), plan.problems().toString());
        assertTrue(plan.problems().stream().allMatch(p -> p.startsWith("bad: skipped: ")));
    }

    @Test
    void reservedAndMalformedNamespacesAreRefused() {
        ContentPlan plan = ContentPlan.of(new LinkedHashMap<>(Map.of(
                "a", module("minecraft", c -> c.item("x")),
                "b", module("Bad NS", c -> c.item("x")),
                "c", module(null, c -> c.item("x")))));
        assertTrue(plan.entries().isEmpty());
        assertEquals(3, plan.problems().size(), plan.problems().toString());
    }

    @Test
    void duplicateIdsAreProblems() {
        ContentPlan plan = ContentPlan.of(Map.of("d", module("d", c -> {
            c.block("thing");
            c.item("thing");          // clashes with the block's item
        })));
        assertTrue(plan.entries().isEmpty());
        assertTrue(plan.problems().get(0).contains("item d:thing declared twice"), plan.problems().toString());
        assertTrue(ContentPlan.of(Map.of("e", module("e", c -> {
            c.block("thing").noItem();
            c.item("thing");          // fine: the block has no item
        }))).problems().isEmpty());
    }

    @Test
    void aThrowingModuleIsSkippedAndOthersStay() {
        Map<String, ContentModule> modules = new LinkedHashMap<>();
        modules.put("boom", module("boom", c -> { throw new IllegalStateException("oops"); }));
        modules.put("ruby-content", RUBY);
        ContentPlan plan = ContentPlan.of(modules);
        assertEquals(List.of("ruby"), plan.entries().stream().map(ContentPlan.Entry::namespace).toList());
        assertTrue(plan.problems().get(0).contains("oops"));
    }

    @Test
    void orderDependsOnNamespacesNotOnDiscoveryOrder() {
        ContentModule zinc = module("zinc", c -> c.item("ingot"));
        ContentModule apple = module("apple", c -> c.item("seed"));
        Map<String, ContentModule> one = new LinkedHashMap<>();
        one.put("z", zinc);
        one.put("a", apple);
        Map<String, ContentModule> two = new LinkedHashMap<>();
        two.put("a", apple);
        two.put("z", zinc);
        assertEquals(ContentPlan.of(one), ContentPlan.of(two));
        assertEquals(List.of("apple", "zinc"), ContentPlan.of(one).entries().stream().map(ContentPlan.Entry::namespace).toList());
    }

    @Test
    void aSharedNamespaceKeepsOneModuleWhateverTheOrder() {
        Map<String, ContentModule> one = new LinkedHashMap<>();
        one.put("second", module("same", c -> c.item("b")));
        one.put("first", module("same", c -> c.item("a")));
        ContentPlan plan = ContentPlan.of(one);
        assertEquals(List.of("first"), plan.entries().stream().map(ContentPlan.Entry::moduleId).toList());
        assertTrue(plan.problems().get(0).contains("already used by first"));
    }

    @Test
    void aBadTabIdIsAProblemAndATabNeedsTheBlockItem() {
        assertTrue(!ContentPlan.of(Map.of("t", module("t", c -> c.item("x").tab("ingredients")))).problems().isEmpty());
        assertTrue(!ContentPlan.of(Map.of("t", module("t", c -> c.block("x").noItem().tab("minecraft:ingredients")))).problems().isEmpty());
    }

    @Test
    void shapesDefaultTheirLayerAndStairsResolveTheirBase() {
        ContentPlan plan = ContentPlan.of(Map.of("s", module("s", c -> {
            c.block("bricks");
            c.block("brick_stairs").stairsOf("bricks");
            c.block("stone_stairs").shape(BlockShape.STAIRS);
            c.block("door").shape(BlockShape.DOOR).material(BlockMaterial.IRON);
            c.block("glass").shape(BlockShape.GLASS).layer(RenderLayer.TRANSLUCENT);
            c.block("log").shape(BlockShape.PILLAR);
        })));
        assertEquals(List.of(), plan.problems());
        List<BlockDeclaration> b = plan.entries().get(0).blocks();
        assertEquals(Optional.of("s:bricks"), b.get(1).base());
        assertEquals(BlockShape.STAIRS, b.get(1).shape());
        assertEquals(Optional.of("minecraft:stone"), b.get(2).base(), "stairs without a base copy stone");
        assertEquals(RenderLayer.CUTOUT, b.get(3).layer(), "doors default to cutout");
        assertEquals(BlockMaterial.IRON, b.get(3).material());
        assertEquals(RenderLayer.TRANSLUCENT, b.get(4).layer(), "an explicit layer wins");
        assertEquals(RenderLayer.SOLID, b.get(5).layer());
    }

    @Test
    void aStairsBaseMustExistBeforeTheStairs() {
        assertTrue(!ContentPlan.of(Map.of("s", module("s", c -> {
            c.block("brick_stairs").stairsOf("bricks");   // declared after
            c.block("bricks");
        }))).problems().isEmpty());
        assertTrue(!ContentPlan.of(Map.of("s", module("s", c -> c.block("x").stairsOf("othermod:bricks")))).problems().isEmpty());
        assertTrue(!ContentPlan.of(Map.of("s", module("s", c -> c.block("x").stairsOf("x")))).problems().isEmpty());
        assertTrue(ContentPlan.of(Map.of("s", module("s", c -> c.block("x").stairsOf("minecraft:oak_planks")))).problems().isEmpty());
    }

    @Test
    void mobsDefaultTheirTextureAndCheckTheirNumbers() {
        ContentPlan plan = ContentPlan.of(Map.of("m", module("ruby", c -> {
            c.mob("ruby_zombie").like(MobArchetype.ZOMBIE).health(30).attackDamage(5).scale(1.25f);
            c.mob("ruby_pig").like(MobArchetype.PIG).texture("entity/pigs/ruby");
        })));
        assertEquals(List.of(), plan.problems());
        List<MobDeclaration> mobs = plan.entries().get(0).mobs();
        assertEquals("ruby:textures/entity/ruby_zombie.png", mobs.get(0).texture(), "texture defaults to entity/<path>");
        assertEquals(java.util.OptionalDouble.of(30), mobs.get(0).health());
        assertEquals(java.util.OptionalDouble.empty(), mobs.get(0).speed(), "unset keeps the archetype's value");
        assertEquals("ruby:textures/entity/pigs/ruby.png", mobs.get(1).texture());
        assertEquals(2, plan.mobCount());

        ContentPlan bad = ContentPlan.of(Map.of("m", module("ruby", c -> {
            c.mob("huge").scale(10);
            c.mob("frail").health(0);
            c.mob("odd").texture("Entity/X");
        })));
        assertEquals(3, bad.problems().size(), bad.problems().toString());
    }

    @Test
    void customMobsResolveTheirFilesAndCheckTheirSettings() {
        ContentPlan plan = ContentPlan.of(Map.of("m", module("ruby", c -> {
            c.mob("ruby_golem").model("ruby_golem").animations("ruby_golem").size(1.4f, 2.7f).health(300).armor(8)
                    .knockbackResistance(1).fireImmune().boss(BossBar.Color.RED, BossBar.Style.NOTCHED_10)
                    .sounds(null, "minecraft:entity.iron_golem.hurt", "minecraft:entity.iron_golem.death");
            c.mob("ruby_sprite").model("ruby_sprite").passive()
                    .goals(MobGoal.swim(), MobGoal.rangedAttack(MobProjectile.SNOWBALL, 40, 12), MobGoal.retaliate());
        })));
        assertEquals(List.of(), plan.problems());
        MobDeclaration golem = plan.entries().get(0).mobs().get(0);
        assertEquals(MobArchetype.CUSTOM, golem.archetype());
        assertTrue(golem.fireImmune());
        MobDeclaration.Custom custom = golem.custom().orElseThrow();
        assertEquals("assets/ruby/conductor/geo/ruby_golem.geo.json", custom.model());
        assertEquals(Optional.of("assets/ruby/conductor/animations/ruby_golem.animation.json"), custom.animations());
        assertTrue(custom.hostile(), "custom mobs are hostile unless passive()");
        assertEquals(MobGoal.Kind.TARGET_PLAYERS, custom.goals().get(custom.goals().size() - 1).kind(), "hostile defaults");
        assertEquals(Optional.of(new BossBar(BossBar.Color.RED, BossBar.Style.NOTCHED_10)), custom.boss());
        assertEquals(Optional.empty(), custom.sounds().ambient());
        MobDeclaration.Custom sprite = plan.entries().get(0).mobs().get(1).custom().orElseThrow();
        assertEquals(3, sprite.goals().size(), "given goals replace the defaults");
        assertTrue(!sprite.hostile());

        ContentPlan bad = ContentPlan.of(Map.of("m", module("ruby", c -> {
            c.mob("zombie_with_goals").like(MobArchetype.ZOMBIE).goals(MobGoal.swim()).boss(BossBar.Color.RED, BossBar.Style.PROGRESS);
            c.mob("no_model").like(MobArchetype.CUSTOM);
            c.mob("flat").model("flat").size(0, 1);
            c.mob("no_projectile").model("x").goals(MobGoal.rangedAttack(null, 20, 10));
            c.mob("bad_sound").model("x").sounds("hurt", null, null);
            c.mob("bad_model").model("Ruby Golem");
            c.mob("heavy").model("x").armor(40);
        })));
        assertEquals(7, bad.problems().size(), bad.problems().toString());
        assertTrue(bad.problems().get(0).contains("goals, boss need a custom mob"), bad.problems().get(0));
        assertEquals(MobArchetype.ZOMBIE, ContentPlan.of(Map.of("m", module("ruby", c -> c.mob("z"))))
                .entries().get(0).mobs().get(0).archetype(), "mobs stay zombies by default");
    }

    @Test
    void modelBlocksNeedValidBoxesAndTurnWithTheBlock() {
        ContentPlan ok = ContentPlan.of(Map.of("m", module("m", c -> c.block("lamp").box(7, 0, 7, 9, 4, 9).box(6, 4, 6, 10, 8, 10)
                .sounds("minecraft:block.lantern.break", "minecraft:block.glass.step", "minecraft:block.lantern.place",
                        "minecraft:block.lantern.hit", "minecraft:block.glass.fall"))));
        assertEquals(List.of(), ok.problems());
        BlockDeclaration lamp = ok.entries().get(0).blocks().get(0);
        assertEquals(BlockShape.MODEL, lamp.shape());
        assertEquals(RenderLayer.CUTOUT, lamp.layer(), "models usually have see-through pixels");
        assertEquals(2, lamp.boxes().size());
        assertTrue(lamp.sounds().isPresent());

        ContentPlan bad = ContentPlan.of(Map.of("m", module("m", c -> {
            c.block("empty").shape(BlockShape.MODEL);
            c.block("outside").box(0, 0, 0, 17, 1, 1);
            c.block("flat").box(0, 0, 0, 16, 0, 16);
            c.block("cube_with_box").box(0, 0, 0, 1, 1, 1).shape(BlockShape.CUBE);
            c.block("noisy").sounds("glass", "minecraft:a", "minecraft:b", "minecraft:c", "minecraft:d");
        })));
        assertEquals(5, bad.problems().size(), bad.problems().toString());

        // a box against the north side, turned with the block, ends up against that side
        BlockBox north = new BlockBox(2, 0, 0, 6, 4, 3);
        assertEquals(north, north.facing("north"));
        assertEquals(new BlockBox(13, 0, 2, 16, 4, 6), north.facing("east"));
        assertEquals(new BlockBox(10, 0, 13, 14, 4, 16), north.facing("south"));
        assertEquals(new BlockBox(0, 0, 10, 3, 4, 14), north.facing("west"));
    }
}
