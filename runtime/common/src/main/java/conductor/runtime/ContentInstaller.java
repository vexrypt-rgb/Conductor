package conductor.runtime;

import conductor.contract.BlockDeclaration;
import conductor.contract.ContentPlan;
import conductor.contract.ItemDeclaration;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Puts declared content into the game, with vanilla's own calls. Called from Conductor's hooks: registration right
 * before the built-in registries freeze, creative tabs when a tab is built, packs when a pack repository is made.
 */
public final class ContentInstaller {

    private static final Logger LOG = LoggerFactory.getLogger("conductor");
    private static boolean installed;
    /** Creative tab id to the items listed in it, in declaration order. */
    private static final Map<String, List<Item>> TAB_ITEMS = new HashMap<>();

    private ContentInstaller() {}

    /**
     * Register every planned block (with its states) and item. Runs once, from the hook at the head of
     * {@code BuiltInRegistries.freeze()}; vanilla's entries are already in, so their ids don't move.
     */
    public static synchronized void beforeRegistryFreeze() {
        if (installed) {
            return;
        }
        installed = true;
        ContentPlan plan = ContentHooks.plan();
        plan.problems().forEach(p -> LOG.warn("Conductor content: {}", p));
        if (plan.entries().isEmpty()) {
            return;
        }
        for (ContentPlan.Entry entry : plan.entries()) {
            // A failure here leaves the registries half-filled. That can't be undone, so it is not hidden: the game
            // then fails the way vanilla would, with the module named in the log.
            try {
                install(entry);
            } catch (RuntimeException | LinkageError e) {
                LOG.error("Conductor content module {} failed while registering; registries are now inconsistent", entry.moduleId(), e);
                throw e;
            }
        }
        LOG.info("Conductor content: registered {} block(s) and {} item(s) from {} module(s) {}", plan.blockCount(),
                plan.itemCount(), plan.entries().size(), plan.entries().stream().map(ContentPlan.Entry::namespace).toList());
        Set<String> tabs = new java.util.HashSet<>();
        BuiltInRegistries.CREATIVE_MODE_TAB.registryKeySet().forEach(k -> tabs.add(VersionIds.id(k)));
        TAB_ITEMS.keySet().stream().filter(t -> !tabs.contains(t))
                .forEach(t -> LOG.warn("Conductor content: creative tab {} does not exist in Minecraft {}; its items are not listed", t, VersionIds.MINECRAFT));
    }

    private static void install(ContentPlan.Entry entry) {
        for (BlockDeclaration d : entry.blocks()) {
            ResourceKey<Block> key = VersionIds.key(Registries.BLOCK, d.id());
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of().setId(key)
                    .strength(d.hardness(), d.resistance()).sound(sound(d.sound()));
            int light = d.light();
            if (light > 0) {
                props.lightLevel(state -> light);
            }
            if (d.requiresTool()) {
                props.requiresCorrectToolForDrops();
            }
            Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(props));
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                // vanilla does this in Blocks' static initialiser, before this block existed; a loader may have done
                // it already when the block was registered
                if (Block.BLOCK_STATE_REGISTRY.getId(state) == -1) {
                    Block.BLOCK_STATE_REGISTRY.add(state);
                }
                state.initCache();
            }
            if (d.withItem()) {
                Item item = Items.registerBlock(block);
                d.tab().ifPresent(t -> TAB_ITEMS.computeIfAbsent(t, k -> new ArrayList<>()).add(item));
            }
        }
        for (ItemDeclaration d : entry.items()) {
            Item.Properties props = new Item.Properties().stacksTo(d.maxStack()).rarity(Rarity.valueOf(d.rarity().name()));
            if (d.durability() > 0) {
                props.durability(d.durability());
            }
            d.food().ifPresent(f -> props.food(new FoodProperties(f.nutrition(), f.saturation(), f.alwaysEdible())));
            if (d.fireResistant()) {
                props.fireResistant();
            }
            Item item = Items.registerItem(VersionIds.key(Registries.ITEM, d.id()), Item::new, props);
            d.tab().ifPresent(t -> TAB_ITEMS.computeIfAbsent(t, k -> new ArrayList<>()).add(item));
        }
    }

    private static SoundType sound(conductor.contract.BlockSound sound) {
        return switch (sound) {
            case STONE -> SoundType.STONE;
            case WOOD -> SoundType.WOOD;
            case METAL -> SoundType.METAL;
            case GLASS -> SoundType.GLASS;
            case GRAVEL -> SoundType.GRAVEL;
            case GRASS -> SoundType.GRASS;
            case SAND -> SoundType.SAND;
            case WOOL -> SoundType.WOOL;
            case AMETHYST -> SoundType.AMETHYST;
        };
    }

    /** From the hook at the end of {@code CreativeModeTab.buildContents}: list module items in their tabs. */
    public static void addTabItems(CreativeModeTab tab, Collection<ItemStack> display, Set<ItemStack> search) {
        if (TAB_ITEMS.isEmpty()) {
            return;
        }
        Optional<ResourceKey<CreativeModeTab>> key = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab);
        if (key.isEmpty()) {
            return;
        }
        List<Item> items = TAB_ITEMS.get(VersionIds.id(key.get()));
        if (items == null) {
            return;
        }
        for (Item item : items) {
            display.add(new ItemStack(item));
            search.add(new ItemStack(item));
        }
    }

    /**
     * The packs for a pack repository of this type: one per content module jar, required (always on), holding its
     * {@code assets/} or {@code data/}, with {@code mc_<version>} as an overlay for files that differ by version.
     * Built with metadata in code, so the jar needs no version-specific {@code pack.mcmeta}.
     */
    public static RepositorySource packSource(PackType type) {
        return add -> {
            for (ContentPlan.Entry entry : ContentHooks.plan().entries()) {
                Path jar = ContentHooks.jars().get(entry.moduleId());
                if (jar == null || !Files.isRegularFile(jar)) {
                    continue;
                }
                try {
                    String name = "conductor/" + entry.namespace();
                    PackLocationInfo location = new PackLocationInfo(name, Component.literal("Conductor: " + entry.namespace()),
                            PackSource.BUILT_IN, Optional.empty());
                    Pack.Metadata metadata = new Pack.Metadata(Component.literal("Content from " + entry.moduleId()),
                            PackCompatibility.COMPATIBLE, FeatureFlags.VANILLA_SET, List.of("mc_" + VersionIds.MINECRAFT));
                    add.accept(new Pack(location, new FilePackResources.FileResourcesSupplier(jar), metadata,
                            new PackSelectionConfig(true, Pack.Position.TOP, false)));
                } catch (RuntimeException | LinkageError e) {
                    LOG.warn("Conductor content: no {} pack for {}: {}", type, entry.moduleId(), e.toString());
                }
            }
        };
    }
}
