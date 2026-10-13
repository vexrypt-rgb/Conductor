package conductor.runtime;

import conductor.contract.ContentId;

import java.util.Map;

/**
 * Minecraft 1.0's numbers as today's ids. Minecraft 1.0 names blocks, items, effects and dimensions by number (and a
 * block or item by number plus "metadata", e.g. wool 35:14 is red); modules see the ids every other version uses,
 * with today's names (1.21: {@code minecraft:short_grass}, {@code minecraft:poppy}, {@code minecraft:zombified_piglin}).
 * The tables come from 1.0.0's own {@code Block}, {@code Item}, {@code StatusEffect} and {@code Entities}
 * initialisers. Touches no Minecraft class.
 */
final class LegacyIds {

    private LegacyIds() {
    }

    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] WOODS = {"oak", "spruce", "birch"};
    /** Slab metadata 0-5 (single slab 44, double slab 43). */
    private static final String[] SLABS = {"smooth_stone_slab", "sandstone_slab", "oak_slab", "cobblestone_slab",
            "brick_slab", "stone_brick_slab"};
    private static final String[] DYES = {"ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye",
            "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye",
            "magenta_dye", "orange_dye", "bone_meal"};

    /** Blocks 0-122, by number; null where the name depends on metadata. */
    private static final String[] BLOCKS = {
            "air", "stone", "grass_block", "dirt", "cobblestone", "oak_planks", null, "bedrock", "water", "water",
            "lava", "lava", "sand", "gravel", "gold_ore", "iron_ore", "coal_ore", null, null, "sponge",
            "glass", "lapis_ore", "lapis_block", "dispenser", "sandstone", "note_block", "red_bed", "powered_rail", "detector_rail", "sticky_piston",
            "cobweb", null, "dead_bush", "piston", "piston_head", null, "moving_piston", "dandelion", "poppy", "brown_mushroom",
            "red_mushroom", "gold_block", "iron_block", null, null, "bricks", "tnt", "bookshelf", "mossy_cobblestone", "obsidian",
            null, "fire", "spawner", "oak_stairs", "chest", "redstone_wire", "diamond_ore", "diamond_block", "crafting_table", "wheat",
            "farmland", "furnace", "furnace", "oak_sign", "oak_door", "ladder", "rail", "cobblestone_stairs", "oak_wall_sign", "lever",
            "stone_pressure_plate", "iron_door", "oak_pressure_plate", "redstone_ore", "redstone_ore", null, null, "stone_button", "snow", "ice",
            "snow_block", "cactus", "clay", "sugar_cane", "jukebox", "oak_fence", "carved_pumpkin", "netherrack", "soul_sand", "glowstone",
            "nether_portal", "jack_o_lantern", "cake", "repeater", "repeater", "chest", "oak_trapdoor", null, null, "brown_mushroom_block",
            null, "iron_bars", "glass_pane", "melon", "pumpkin_stem", "melon_stem", "vine", "oak_fence_gate", "brick_stairs", "stone_brick_stairs",
            "mycelium", "lily_pad", "nether_bricks", "nether_brick_fence", "nether_brick_stairs", "nether_wart", "enchanting_table", "brewing_stand", "cauldron", "end_portal",
            "end_portal_frame", "end_stone", "dragon_egg"};

    /** Items 256-382, by number minus 256; null where the name depends on metadata. */
    private static final String[] ITEMS = {
            "iron_shovel", "iron_pickaxe", "iron_axe", "flint_and_steel", "apple", "bow", "arrow", null, "diamond", "iron_ingot",
            "gold_ingot", "iron_sword", "wooden_sword", "wooden_shovel", "wooden_pickaxe", "wooden_axe", "stone_sword", "stone_shovel", "stone_pickaxe", "stone_axe",
            "diamond_sword", "diamond_shovel", "diamond_pickaxe", "diamond_axe", "stick", "bowl", "mushroom_stew", "golden_sword", "golden_shovel", "golden_pickaxe",
            "golden_axe", "string", "feather", "gunpowder", "wooden_hoe", "stone_hoe", "iron_hoe", "diamond_hoe", "golden_hoe", "wheat_seeds",
            "wheat", "bread", "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots",
            "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots", "golden_helmet", "golden_chestplate",
            "golden_leggings", "golden_boots", "flint", "porkchop", "cooked_porkchop", "painting", "golden_apple", "oak_sign", "oak_door", "bucket",
            "water_bucket", "lava_bucket", "minecart", "saddle", "iron_door", "redstone", "snowball", "oak_boat", "leather", "milk_bucket",
            "brick", "clay_ball", "sugar_cane", "paper", "book", "slime_ball", "chest_minecart", "furnace_minecart", "egg", "compass",
            "fishing_rod", "clock", "glowstone_dust", "cod", "cooked_cod", null, "bone", "sugar", "cake", "red_bed",
            "repeater", "cookie", "filled_map", "shears", "melon_slice", "pumpkin_seeds", "melon_seeds", "beef", "cooked_beef", "chicken",
            "cooked_chicken", "rotten_flesh", "ender_pearl", "blaze_rod", "ghast_tear", "gold_nugget", "nether_wart", "potion", "glass_bottle", "spider_eye",
            "fermented_spider_eye", "blaze_powder", "magma_cream", "brewing_stand", "cauldron", "ender_eye", "glistering_melon_slice"};

    /** Music discs 2256-2266. */
    private static final String[] DISCS = {"13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11"};

    /** Status effects 1-19. */
    private static final String[] EFFECTS = {null, "speed", "slowness", "haste", "mining_fatigue", "strength",
            "instant_health", "instant_damage", "jump_boost", "nausea", "regeneration", "resistance", "fire_resistance",
            "water_breathing", "invisibility", "blindness", "night_vision", "hunger", "weakness", "poison"};

    /** 1.0's entity names ({@code Entities}) as today's ids. */
    private static final Map<String, String> ENTITIES = Map.ofEntries(
            Map.entry("Item", "item"), Map.entry("XPOrb", "experience_orb"), Map.entry("Painting", "painting"),
            Map.entry("Arrow", "arrow"), Map.entry("Snowball", "snowball"), Map.entry("Fireball", "fireball"),
            Map.entry("SmallFireball", "small_fireball"), Map.entry("ThrownEnderpearl", "ender_pearl"),
            Map.entry("EyeOfEnderSignal", "eye_of_ender"), Map.entry("PrimedTnt", "tnt"),
            Map.entry("FallingSand", "falling_block"), Map.entry("Minecart", "minecart"), Map.entry("Boat", "oak_boat"),
            Map.entry("Creeper", "creeper"), Map.entry("Skeleton", "skeleton"), Map.entry("Spider", "spider"),
            Map.entry("Giant", "giant"), Map.entry("Zombie", "zombie"), Map.entry("Slime", "slime"),
            Map.entry("Ghast", "ghast"), Map.entry("PigZombie", "zombified_piglin"), Map.entry("Enderman", "enderman"),
            Map.entry("CaveSpider", "cave_spider"), Map.entry("Silverfish", "silverfish"), Map.entry("Blaze", "blaze"),
            Map.entry("LavaSlime", "magma_cube"), Map.entry("EnderDragon", "ender_dragon"), Map.entry("Pig", "pig"),
            Map.entry("Sheep", "sheep"), Map.entry("Cow", "cow"), Map.entry("Chicken", "chicken"),
            Map.entry("Squid", "squid"), Map.entry("Wolf", "wolf"), Map.entry("MushroomCow", "mooshroom"),
            Map.entry("SnowMan", "snow_golem"), Map.entry("Villager", "villager"), Map.entry("EnderCrystal", "end_crystal"));

    /** The id of block {@code id} with {@code metadata}; {@code minecraft:legacy_<id>} for a number 1.0 does not have. */
    static String block(int id, int metadata) {
        String name = id >= 0 && id < BLOCKS.length ? BLOCKS[id] : null;
        if (name == null) {
            name = blockByMetadata(id, metadata);
        }
        return name == null ? "minecraft:legacy_" + id : "minecraft:" + name;
    }

    private static String blockByMetadata(int id, int m) {
        return switch (id) {
            case 6 -> wood(m & 3) + "_sapling";
            case 17 -> wood(m & 3) + "_log";
            case 18 -> wood(m & 3) + "_leaves";
            case 31 -> m == 1 ? "short_grass" : m == 2 ? "fern" : "dead_bush";
            case 35 -> COLORS[m & 15] + "_wool";
            case 43, 44 -> SLABS[(m & 7) < SLABS.length ? m & 7 : 0];
            // a torch's metadata is the wall it hangs on (1-4), or 5 for standing
            case 50 -> m >= 1 && m <= 4 ? "wall_torch" : "torch";
            case 75, 76 -> m >= 1 && m <= 4 ? "redstone_wall_torch" : "redstone_torch";
            case 97 -> m == 1 ? "infested_cobblestone" : m == 2 ? "infested_stone_bricks" : "infested_stone";
            case 98 -> m == 1 ? "mossy_stone_bricks" : m == 2 ? "cracked_stone_bricks" : "stone_bricks";
            case 100 -> m == 10 ? "mushroom_stem" : "red_mushroom_block";
            default -> null;
        };
    }

    private static String wood(int m) {
        return m < WOODS.length ? WOODS[m] : WOODS[0];
    }

    /** The id of item {@code id} with {@code metadata} (below 256 it is a block's item). */
    static String item(int id, int metadata) {
        if (id > 0 && id < 256) {
            return block(id, metadata);
        }
        int n = id - 256;
        if (n >= 0 && n < ITEMS.length) {
            String name = ITEMS[n];
            if (name == null) {
                name = n == 7 ? (metadata == 1 ? "charcoal" : "coal") : DYES[metadata & 15];
            }
            return "minecraft:" + name;
        }
        if (id >= 2256 && id < 2256 + DISCS.length) {
            return "minecraft:music_disc_" + DISCS[id - 2256];
        }
        return "minecraft:legacy_" + id;
    }

    /** The id of status effect {@code id}. */
    static String effect(int id) {
        return id > 0 && id < EFFECTS.length ? "minecraft:" + EFFECTS[id] : "minecraft:legacy_effect_" + id;
    }

    /** The id of a 1.0 entity name ({@code Entities.getKey}); null keys are players or unregistered entities. */
    static String entity(String key) {
        String name = key == null ? null : ENTITIES.get(key);
        return name == null ? "minecraft:unknown" : "minecraft:" + name;
    }

    /**
     * 1.0 has no player UUIDs. Conductor gives each name the id an offline-mode server gives it today, so a module sees
     * the same id for a name as on any newer offline server.
     */
    static java.util.UUID offlineId(String name) {
        return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 1.0's dimension numbers. */
    static String dimension(int id) {
        return switch (id) {
            case 0 -> "minecraft:overworld";
            case -1 -> "minecraft:the_nether";
            case 1 -> "minecraft:the_end";
            default -> "minecraft:legacy_dimension_" + id;
        };
    }

    private static volatile Map<ContentId.Kind, java.util.Set<String>> known;

    /** Every id of this kind 1.0 has, or null for a kind it has no table for. */
    static java.util.Set<String> known(ContentId.Kind kind) {
        Map<ContentId.Kind, java.util.Set<String>> k = known;
        if (k == null) {
            java.util.Set<String> blocks = new java.util.TreeSet<>();
            java.util.Set<String> items = new java.util.TreeSet<>();
            for (int id = 1; id < BLOCKS.length; id++) {
                for (int m = 0; m < 16; m++) {
                    blocks.add(block(id, m));
                }
            }
            for (int id = 256; id < 256 + ITEMS.length; id++) {
                for (int m = 0; m < 16; m++) {
                    items.add(item(id, m));
                }
            }
            for (int id = 2256; id < 2256 + DISCS.length; id++) {
                items.add(item(id, 0));
            }
            items.addAll(blocks); // a block is its own item in 1.0
            java.util.Set<String> effects = new java.util.TreeSet<>();
            for (int id = 1; id < EFFECTS.length; id++) {
                effects.add(effect(id));
            }
            java.util.Set<String> entities = new java.util.TreeSet<>();
            ENTITIES.values().forEach(e -> entities.add("minecraft:" + e));
            entities.add("minecraft:player");
            k = Map.of(ContentId.Kind.BLOCK, java.util.Set.copyOf(blocks), ContentId.Kind.ITEM, java.util.Set.copyOf(items),
                    ContentId.Kind.EFFECT, java.util.Set.copyOf(effects), ContentId.Kind.ENTITY, java.util.Set.copyOf(entities));
            known = k;
        }
        return k.get(kind);
    }

    /** The dimension number of an id, or null if 1.0 has no such dimension. */
    static Integer dimensionNumber(String id) {
        return switch (id) {
            case "minecraft:overworld" -> 0;
            case "minecraft:the_nether" -> -1;
            case "minecraft:the_end" -> 1;
            default -> null;
        };
    }

    private static volatile Map<String, int[]> itemNumbers;

    /** The number and metadata 1.0 gives item {@code id} (namespace optional), or null if 1.0 has no such item. */
    static int[] itemNumber(String id) {
        Map<String, int[]> numbers = itemNumbers;
        if (numbers == null) {
            Map<String, int[]> n = new java.util.HashMap<>();
            for (int number = 2256 + DISCS.length - 1; number > 0; number--) {
                if (number >= 256 + ITEMS.length && number < 2256) {
                    continue;
                }
                for (int m = 15; m >= 0; m--) { // the lowest number and metadata win: 17:0 is oak_log, not 17:4
                    String name = item(number, m);
                    if (!name.startsWith("minecraft:legacy_")) {
                        n.put(name, new int[] {number, m});
                    }
                }
            }
            numbers = itemNumbers = Map.copyOf(n);
        }
        return numbers.get(id.indexOf(':') < 0 ? "minecraft:" + id : id);
    }

    /**
     * A command in today's syntax, in 1.0's where Conductor knows how: {@code give <player> <item> [count]} becomes
     * {@code give <player> <number> [count] [metadata]}. Anything else is left as it is.
     */
    static String command(String command) {
        String[] w = command.trim().split("\\s+");
        if (w.length >= 3 && w.length <= 4 && w[0].equalsIgnoreCase("give") && !w[2].chars().allMatch(Character::isDigit)) {
            int[] item = itemNumber(w[2]);
            if (item != null) {
                String count = w.length == 4 ? w[3] : "1";
                return "give " + w[1] + " " + item[0] + " " + count + (item[1] != 0 ? " " + item[1] : "");
            }
        }
        return command;
    }
}
