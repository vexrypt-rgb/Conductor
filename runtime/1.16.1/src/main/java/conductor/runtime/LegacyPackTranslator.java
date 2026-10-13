package conductor.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a content module's files, written for Minecraft 1.21, into the layout and formats Minecraft 1.16.1 reads.
 * Only mechanical changes are made: folder names that were renamed, recipe JSON that changed shape, item models that
 * 1.21 keeps in {@code items/}, and vanilla door models that were renamed. Files under {@code mc_1.16.1/} are this
 * version's own and replace the translated ones unchanged. Pure: it reads and returns bytes, so it is tested without
 * a game.
 */
public final class LegacyPackTranslator {

    /** The overlay folder for this version: files in it are used as they are. */
    public static final String OVERLAY = "mc_1.16.1/";

    /** {@code data/<ns>/<folder>/}: 1.21 folder name to 1.16 folder name. */
    private static final Map<String, String> DATA_FOLDERS = Map.of(
            "recipe", "recipes", "loot_table", "loot_tables", "advancement", "advancements", "predicate", "predicates",
            "item_modifier", "item_modifiers", "structure", "structures", "function", "functions");
    /** {@code data/<ns>/tags/<folder>/}. */
    private static final Map<String, String> TAG_FOLDERS = Map.of(
            "block", "blocks", "item", "items", "entity_type", "entity_types", "fluid", "fluids");
    /** Data folders 1.16.1 cannot read at all (custom dimensions arrived in 1.16.2). */
    private static final List<String> UNSUPPORTED_DATA = List.of("dimension", "dimension_type", "worldgen");
    /**
     * Vanilla door models were renamed in 1.19.3; the block states turn them the same way in both versions
     * (checked against both client jars), so the old model with the same look is a rename. 1.16 names the hinge-side
     * templates {@code door_*_rh} (its {@code oak_door_*_hinge} models use them as parent).
     */
    private static final Map<String, String> VANILLA_MODELS = Map.of(
            "block/door_bottom_left", "block/door_bottom",
            "block/door_bottom_left_open", "block/door_bottom_rh",
            "block/door_bottom_right", "block/door_bottom_rh",
            "block/door_bottom_right_open", "block/door_bottom",
            "block/door_top_left", "block/door_top",
            "block/door_top_left_open", "block/door_top_rh",
            "block/door_top_right", "block/door_top_rh",
            "block/door_top_right_open", "block/door_top");
    private static final List<String> COOKING = List.of("minecraft:smelting", "minecraft:blasting", "minecraft:smoking",
            "minecraft:campfire_cooking");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** The translated files (1.16.1 path to content) and what could not be carried over. */
    public record Result(Map<String, byte[]> files, List<String> notes) {}

    private LegacyPackTranslator() {}

    /**
     * @param files a module jar's files, by path inside the jar; only {@code <root>/...} and
     *              {@code mc_1.16.1/<root>/...} are used
     * @param root  {@code assets} or {@code data}
     */
    public static Result translate(Map<String, byte[]> files, String root) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        Map<String, byte[]> overlay = new LinkedHashMap<>();
        files.forEach((path, bytes) -> {
            if (path.startsWith(OVERLAY + root + "/")) {
                overlay.put(path.substring(OVERLAY.length()), bytes);
            }
        });
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            if (!path.startsWith(root + "/") || path.endsWith("/")) {
                continue;
            }
            try {
                String to = root.equals("data") ? dataPath(path, notes) : path;
                if (to == null) {
                    continue;
                }
                byte[] bytes = e.getValue();
                if (to.contains("/recipes/") && to.endsWith(".json")) {
                    bytes = json(recipe(parse(bytes), path, notes));
                } else if (root.equals("assets") && to.matches("assets/[^/]+/models/.+\\.json")) {
                    bytes = model(bytes);
                } else if (root.equals("assets") && to.matches("assets/[^/]+/items/.+\\.json")) {
                    itemModel(path, parse(bytes), files, overlay, out, notes);
                    continue; // 1.16.1 has no item model definitions; the model is written instead
                }
                out.put(to, bytes);
            } catch (RuntimeException ex) {
                notes.add(path + ": not translated (" + ex.getMessage() + "); left out");
            }
        }
        out.putAll(overlay);
        return new Result(out, notes);
    }

    /** {@code data/<ns>/<folder>/...} with 1.16's folder names; null to leave the file out. */
    private static String dataPath(String path, List<String> notes) {
        String[] p = path.split("/", 4);
        if (p.length < 4) {
            return path;
        }
        String ns = p[1], folder = p[2], rest = p[3];
        if (UNSUPPORTED_DATA.contains(folder)) {
            notes.add(path + ": " + folder + " files need Minecraft 1.16.2 or later; left out");
            return null;
        }
        if (folder.equals("tags")) {
            String[] t = rest.split("/", 2);
            if (t.length == 2 && TAG_FOLDERS.containsKey(t[0])) {
                return "data/" + ns + "/tags/" + TAG_FOLDERS.get(t[0]) + "/" + t[1];
            }
            return path;
        }
        return "data/" + ns + "/" + DATA_FOLDERS.getOrDefault(folder, folder) + "/" + rest;
    }

    /** A 1.21 recipe (string ingredients, {@code result.id}) in 1.16's shape ({@code {"item": ...}}, {@code result.item}). */
    static JsonObject recipe(JsonObject r, String path, List<String> notes) {
        String type = r.has("type") ? r.get("type").getAsString() : "";
        if (!type.contains(":")) {
            type = "minecraft:" + type;
        }
        if (r.has("key") && r.get("key").isJsonObject()) {
            JsonObject key = r.getAsJsonObject("key");
            for (Map.Entry<String, JsonElement> k : new ArrayList<>(key.entrySet())) {
                key.add(k.getKey(), ingredient(k.getValue()));
            }
        }
        if (r.has("ingredients") && r.get("ingredients").isJsonArray()) {
            JsonArray in = new JsonArray();
            r.getAsJsonArray("ingredients").forEach(i -> in.add(ingredient(i)));
            r.add("ingredients", in);
        }
        for (String single : List.of("ingredient", "base", "addition")) {
            if (r.has(single)) {
                r.add(single, ingredient(r.get(single)));
            }
        }
        if (r.has("result") && r.get("result").isJsonObject()) {
            JsonObject result = r.getAsJsonObject("result");
            String item = result.has("id") ? result.get("id").getAsString() : result.has("item") ? result.get("item").getAsString() : null;
            int count = result.has("count") ? result.get("count").getAsInt() : 1;
            if (result.has("components")) {
                notes.add(path + ": result components are not supported by Minecraft 1.16.1; the plain item is made");
            }
            if (item != null && (COOKING.contains(type) || type.equals("minecraft:stonecutting"))) {
                r.addProperty("result", item); // 1.16 cooking and stonecutting results are a plain id
                if (type.equals("minecraft:stonecutting")) {
                    r.addProperty("count", count);
                } else if (count != 1) {
                    notes.add(path + ": Minecraft 1.16.1 cooks one item at a time; count " + count + " dropped");
                }
            } else if (item != null) {
                JsonObject old = new JsonObject();
                old.addProperty("item", item);
                if (count != 1) {
                    old.addProperty("count", count);
                }
                r.add("result", old);
            }
        }
        return r;
    }

    /** {@code "ns:item"} to {@code {"item": ...}}, {@code "#ns:tag"} to {@code {"tag": ...}}; arrays element by element. */
    static JsonElement ingredient(JsonElement e) {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString();
            JsonObject o = new JsonObject();
            if (s.startsWith("#")) {
                o.addProperty("tag", s.substring(1));
            } else {
                o.addProperty("item", s);
            }
            return o;
        }
        if (e.isJsonArray()) {
            JsonArray a = new JsonArray();
            e.getAsJsonArray().forEach(x -> a.add(ingredient(x)));
            return a;
        }
        return e;
    }

    /** A block or item model with renamed vanilla parents pointed at their 1.16 names. */
    private static byte[] model(byte[] bytes) {
        JsonObject m = parse(bytes);
        if (m.has("parent") && m.get("parent").isJsonPrimitive()) {
            String parent = m.get("parent").getAsString();
            String path = parent.startsWith("minecraft:") ? parent.substring("minecraft:".length()) : parent;
            String renamed = parent.contains(":") && !parent.startsWith("minecraft:") ? null : VANILLA_MODELS.get(path);
            if (renamed != null) {
                m.add("parent", new JsonPrimitive("minecraft:" + renamed));
                return json(m);
            }
        }
        return bytes;
    }

    /**
     * {@code assets/<ns>/items/<x>.json} (1.21.4's item model definitions) to {@code models/item/<x>.json} with the
     * referenced model as parent, unless the module ships that item model itself.
     */
    private static void itemModel(String path, JsonObject def, Map<String, byte[]> files, Map<String, byte[]> overlay,
                                  Map<String, byte[]> out, List<String> notes) {
        String[] p = path.split("/", 4); // assets, ns, items, x.json
        String target = "assets/" + p[1] + "/models/item/" + p[3];
        if (files.containsKey(target) || overlay.containsKey(target)) {
            return;
        }
        JsonElement model = def.get("model");
        if (model != null && model.isJsonObject()) {
            JsonObject m = model.getAsJsonObject();
            String type = m.has("type") ? m.get("type").getAsString() : "";
            if ((type.equals("minecraft:model") || type.equals("model")) && m.has("model")) {
                JsonObject item = new JsonObject();
                item.addProperty("parent", m.get("model").getAsString());
                out.put(target, json(item));
                return;
            }
            notes.add(path + ": item model type " + type + " has no Minecraft 1.16.1 form; add " + OVERLAY + target);
            return;
        }
        notes.add(path + ": no model in this item model definition");
    }

    private static JsonObject parse(byte[] bytes) {
        JsonElement e = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8));
        if (!e.isJsonObject()) {
            throw new IllegalArgumentException("not a JSON object");
        }
        return e.getAsJsonObject();
    }

    private static byte[] json(JsonElement e) {
        return GSON.toJson(e).getBytes(StandardCharsets.UTF_8);
    }
}
