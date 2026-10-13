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
}
