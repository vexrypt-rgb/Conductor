package conductor.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.21-to-1.16.1 file translation, on files shaped like the ruby example's. */
class LegacyPackTranslatorTest {

    private static Map<String, byte[]> files(String... pathThenJson) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        for (int i = 0; i < pathThenJson.length; i += 2) {
            m.put(pathThenJson[i], pathThenJson[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return m;
    }

    private static JsonObject json(byte[] bytes) {
        return new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void dataFoldersAreRenamedAndDimensionsLeftOut() {
        LegacyPackTranslator.Result r = LegacyPackTranslator.translate(files(
                "data/ruby/loot_table/blocks/ruby_block.json", "{\"type\":\"minecraft:block\"}",
                "data/minecraft/tags/block/mineable/pickaxe.json", "{\"values\":[\"ruby:ruby_block\"]}",
                "data/ruby/dimension/ruby_world.json", "{}",
                "assets/ruby/lang/en_us.json", "{}"), "data");
        assertEquals(Set.of("data/ruby/loot_tables/blocks/ruby_block.json", "data/minecraft/tags/blocks/mineable/pickaxe.json"),
                r.files().keySet());
        assertEquals(1, r.notes().size());
        assertTrue(r.notes().get(0).contains("1.16.2"));
    }

    @Test
    void shapedAndShapelessRecipes() {
        LegacyPackTranslator.Result r = LegacyPackTranslator.translate(files(
                "data/ruby/recipe/ruby_block.json",
                "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"###\"],\"key\":{\"#\":\"ruby:ruby\",\"L\":\"#minecraft:logs\"},"
                        + "\"result\":{\"id\":\"ruby:ruby_block\",\"count\":1}}",
                "data/ruby/recipe/ruby_from_block.json",
                "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"ruby:ruby_block\",[\"a:b\",\"#c:d\"]],"
                        + "\"result\":{\"id\":\"ruby:ruby\",\"count\":9}}"), "data");
        JsonObject shaped = json(r.files().get("data/ruby/recipes/ruby_block.json"));
        assertEquals("ruby:ruby", shaped.getAsJsonObject("key").getAsJsonObject("#").get("item").getAsString());
        assertEquals("minecraft:logs", shaped.getAsJsonObject("key").getAsJsonObject("L").get("tag").getAsString());
        assertEquals("ruby:ruby_block", shaped.getAsJsonObject("result").get("item").getAsString());
        assertFalse(shaped.getAsJsonObject("result").has("count"));
        JsonObject shapeless = json(r.files().get("data/ruby/recipes/ruby_from_block.json"));
        assertEquals("ruby:ruby_block", shapeless.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("c:d", shapeless.getAsJsonArray("ingredients").get(1).getAsJsonArray().get(1).getAsJsonObject().get("tag").getAsString());
        assertEquals(9, shapeless.getAsJsonObject("result").get("count").getAsInt());
        assertTrue(r.notes().isEmpty());
    }

    @Test
    void cookingAndStonecuttingResultsArePlainIds() {
        LegacyPackTranslator.Result r = LegacyPackTranslator.translate(files(
                "data/ruby/recipe/smelt.json",
                "{\"type\":\"minecraft:smelting\",\"ingredient\":\"ruby:ore\",\"result\":{\"id\":\"ruby:ruby\",\"count\":2},\"experience\":1}",
                "data/ruby/recipe/cut.json",
                "{\"type\":\"stonecutting\",\"ingredient\":\"ruby:ruby_block\",\"result\":{\"id\":\"ruby:ruby_slab\",\"count\":2}}"), "data");
        JsonObject smelt = json(r.files().get("data/ruby/recipes/smelt.json"));
        assertEquals("ruby:ruby", smelt.get("result").getAsString());
        assertEquals("ruby:ore", smelt.getAsJsonObject("ingredient").get("item").getAsString());
        JsonObject cut = json(r.files().get("data/ruby/recipes/cut.json"));
        assertEquals("ruby:ruby_slab", cut.get("result").getAsString());
        assertEquals(2, cut.get("count").getAsInt());
        assertEquals(1, r.notes().size()); // smelting count 2 can't be kept
    }

    @Test
    void itemModelDefinitionsBecomeItemModelsAndDoorParentsAreRenamed() {
        LegacyPackTranslator.Result r = LegacyPackTranslator.translate(files(
                "assets/ruby/items/ruby_block.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"ruby:block/ruby_block\"}}",
                "assets/ruby/items/ruby.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"ruby:item/ruby\"}}",
                "assets/ruby/models/item/ruby.json", "{\"parent\":\"minecraft:item/generated\"}",
                "assets/ruby/items/odd.json", "{\"model\":{\"type\":\"minecraft:condition\"}}",
                "assets/ruby/models/block/ruby_door_top_left.json", "{\"parent\":\"minecraft:block/door_top_left\",\"textures\":{}}",
                "assets/ruby/models/block/ruby_door_top_right.json", "{\"parent\":\"block/door_top_right\"}",
                "assets/ruby/textures/block/ruby_block.png", "png"), "assets");
        assertEquals("ruby:block/ruby_block", json(r.files().get("assets/ruby/models/item/ruby_block.json")).get("parent").getAsString());
        assertEquals("minecraft:item/generated", json(r.files().get("assets/ruby/models/item/ruby.json")).get("parent").getAsString());
        assertFalse(r.files().keySet().stream().anyMatch(p -> p.contains("/items/")));
        assertEquals("minecraft:block/door_top", json(r.files().get("assets/ruby/models/block/ruby_door_top_left.json")).get("parent").getAsString());
        // 1.16's hinge-side template (vanilla's oak_door_top_hinge has it as parent)
        assertEquals("minecraft:block/door_top_rh", json(r.files().get("assets/ruby/models/block/ruby_door_top_right.json")).get("parent").getAsString());
        assertEquals("png", new String(r.files().get("assets/ruby/textures/block/ruby_block.png"), StandardCharsets.UTF_8));
        assertEquals(1, r.notes().size());
    }

    @Test
    void overlayFilesWinUnchanged() {
        LegacyPackTranslator.Result r = LegacyPackTranslator.translate(files(
                "data/ruby/recipe/x.json", "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"a:b\"],\"result\":{\"id\":\"a:c\"}}",
                "mc_1.16.1/data/ruby/recipes/x.json", "{\"mine\":true}",
                "data/ruby/recipe/broken.json", "{not json"), "data");
        assertEquals("{\"mine\":true}", new String(r.files().get("data/ruby/recipes/x.json"), StandardCharsets.UTF_8));
        assertFalse(r.files().containsKey("data/ruby/recipes/broken.json"));
        assertEquals(1, r.notes().size());
    }

    @Test
    void translatedPackListsLikeAZipPack() throws Exception {
        TranslatedPack pack = new TranslatedPack(new File("module.jar"), files(
                "data/ruby/recipes/a.json", "{}",
                "data/ruby/recipes/sub/b.json", "{}",
                "data/ruby/loot_tables/c.json", "{}",
                "data/ruby/recipes/a.json.mcmeta", "{}",
                "pack.mcmeta", "{\"pack\":{\"pack_format\":5,\"description\":\"x\"}}"));
        Collection<ResourceLocation> recipes = pack.getResources(PackType.SERVER_DATA, "ruby", "recipes", Integer.MAX_VALUE, n -> n.endsWith(".json"));
        assertEquals(Set.of(new ResourceLocation("ruby", "recipes/a.json"), new ResourceLocation("ruby", "recipes/sub/b.json")), Set.copyOf(recipes));
        assertEquals(Set.of("ruby"), pack.getNamespaces(PackType.SERVER_DATA));
        assertTrue(pack.getNamespaces(PackType.CLIENT_RESOURCES).isEmpty());
        assertTrue(pack.hasResource(PackType.SERVER_DATA, new ResourceLocation("ruby", "loot_tables/c.json")));
    }
}
