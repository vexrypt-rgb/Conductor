package conductor.runtime;

import com.google.common.collect.ImmutableSet;
import conductor.contract.BlockDeclaration;
import conductor.contract.BlockMaterial;
import conductor.contract.BlockShape;
import conductor.contract.ContentPlan;
import conductor.contract.ItemDeclaration;
import conductor.contract.MobDeclaration;
import conductor.contract.RenderLayer;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StoneButtonBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WoodButtonBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Puts declared content into Minecraft 1.16.1 with that version's own calls. Registration runs at the end of
 * {@code Bootstrap.bootStrap()}, when vanilla's entries are in; packs are added where vanilla adds its built-in pack.
 * A module's files are written for 1.21 and are translated for this version by {@link LegacyPackTranslator}.
 */
public final class ContentInstaller {

    private static final Logger LOG = LogManager.getLogger("conductor");
    /** The resource and data pack format of Minecraft 1.16.1 (1.16.2 moved data to 6). */
    static final int PACK_FORMAT = 5;
    /** The Minecraft version, for files a module ships per version ({@code mc_1.16.1/...}). */
    public static final String MINECRAFT = "1.16.1";
    private static boolean installed;
    private static final Map<Block, RenderLayer> RENDER_LAYERS = new LinkedHashMap<>();
    private static final Map<EntityType<?>, AttributeSupplier> MOB_ATTRIBUTES = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, MobDeclaration> MOBS = new LinkedHashMap<>();
    /** Translated files per module jar and pack type: translated once, read by every reload. */
    private static final Map<String, Map<String, byte[]>> PACKS = new ConcurrentHashMap<>();

    private ContentInstaller() {}

    /** Register every planned block (with its states), item and mob. Runs once, from the hook at the end of bootstrap. */
    public static synchronized void afterBootstrap() {
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
            // as on 1.21: a half-registered module can't be undone, so the failure is not hidden
            try {
                install(entry);
            } catch (RuntimeException | LinkageError e) {
                LOG.error("Conductor content module {} failed while registering; registries are now inconsistent", entry.moduleId(), e);
                throw e;
            }
        }
        LOG.info("Conductor content: registered {} block(s), {} item(s) and {} mob(s) from {} module(s) {}", plan.blockCount(),
                plan.itemCount(), plan.mobCount(), plan.entries().size(),
                plan.entries().stream().map(ContentPlan.Entry::namespace).collect(java.util.stream.Collectors.toList()));
    }

    private static void install(ContentPlan.Entry entry) {
        for (BlockDeclaration d : entry.blocks()) {
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of(material(d))
                    .strength(d.hardness(), d.resistance())
                    .sound(d.sounds().map(ContentInstaller::sounds).orElseGet(() -> sound(d.sound())));
            int light = d.light();
            if (light > 0) {
                props.lightLevel(state -> light);
            }
            if (d.requiresTool()) {
                props.requiresCorrectToolForDrops();
            }
            Block block = Registry.register(Registry.BLOCK, new ResourceLocation(d.id()), create(d, props));
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                // vanilla does this in Blocks' static initialiser, before this block existed
                if (Block.BLOCK_STATE_REGISTRY.getId(state) == -1) {
                    Block.BLOCK_STATE_REGISTRY.add(state);
                }
                state.initCache();
            }
            if (d.layer() != RenderLayer.SOLID) {
                RENDER_LAYERS.put(block, d.layer());
            }
            if (d.withItem()) {
                Item.Properties itemProps = new Item.Properties();
                d.tab().map(t -> tab(t, d.id())).ifPresent(itemProps::tab);
                BlockItem item = d.shape() == BlockShape.DOOR ? new DoubleHighBlockItem(block, itemProps) : new BlockItem(block, itemProps);
                Registry.register(Registry.ITEM, new ResourceLocation(d.id()), item);
                item.registerBlocks(Item.BY_BLOCK, item);
            }
        }
        for (MobDeclaration d : entry.mobs()) {
            EntityType<? extends Mob> type = Registry.register(Registry.ENTITY_TYPE, new ResourceLocation(d.id()),
                    mobType(d));
            MOBS.put(type, d);
        }
        for (ItemDeclaration d : entry.items()) {
            Item.Properties props = new Item.Properties().stacksTo(d.maxStack()).rarity(Rarity.valueOf(d.rarity().name()));
            if (d.durability() > 0) {
                props.durability(d.durability());
            }
            d.food().ifPresent(f -> {
                // the contract's saturation is the amount restored; 1.16 takes a modifier (amount = 2 * nutrition * mod)
                FoodProperties.Builder food = new FoodProperties.Builder().nutrition(f.nutrition())
                        .saturationMod(f.nutrition() == 0 ? 0f : f.saturation() / (2f * f.nutrition()));
                if (f.alwaysEdible()) {
                    food.alwaysEat();
                }
                props.food(food.build());
            });
            if (d.fireResistant()) {
                props.fireResistant();
            }
            d.tab().map(t -> tab(t, d.id())).ifPresent(props::tab);
            Registry.register(Registry.ITEM, new ResourceLocation(d.id()), new Item(props));
        }
    }

    /** A new entity type with the archetype's class and a scaled hit box. The constructor, not the builder, so no data-fixer lookup. */
    private static EntityType<? extends Mob> mobType(MobDeclaration d) {
        float k = d.scale();
        switch (d.archetype()) {
            case ZOMBIE:
                return new EntityType<Zombie>(Zombie::new, MobCategory.MONSTER, true, true, d.fireImmune(), false, ImmutableSet.of(),
                        EntityDimensions.scalable(0.6f * k, 1.95f * k), 8, 3);
            case SPIDER:
                return new EntityType<Spider>(Spider::new, MobCategory.MONSTER, true, true, d.fireImmune(), false, ImmutableSet.of(),
                        EntityDimensions.scalable(1.4f * k, 0.9f * k), 8, 3);
            case PIG:
                return new EntityType<Pig>(Pig::new, MobCategory.CREATURE, true, true, d.fireImmune(), false, ImmutableSet.of(),
                        EntityDimensions.scalable(0.9f * k, 0.9f * k), 10, 3);
            case CUSTOM:
                MobDeclaration.Custom c = d.custom().orElseThrow();
                EntityDimensions size = EntityDimensions.scalable(c.width() * k, c.height() * k);
                return c.hostile()
                        ? new EntityType<ConductorMob>(ConductorMob.Hostile::new, MobCategory.MONSTER, true, true, d.fireImmune(), false,
                                ImmutableSet.of(), size, 10, 3)
                        : new EntityType<ConductorMob>(ConductorMob::new, MobCategory.CREATURE, true, true, d.fireImmune(), false,
                                ImmutableSet.of(), size, 10, 3);
            default:
                throw new IllegalArgumentException("no Minecraft 1.16.1 class for mob archetype " + d.archetype());
        }
    }

    /** 1.21's creative tab ids to 1.16's fixed tabs; null (with a warning) when there is no counterpart. */
    private static CreativeModeTab tab(String id, String what) {
        switch (id) {
            case "minecraft:building_blocks":
            case "minecraft:colored_blocks":
            case "minecraft:natural_blocks":
                return CreativeModeTab.TAB_BUILDING_BLOCKS;
            case "minecraft:functional_blocks":
                return CreativeModeTab.TAB_DECORATIONS;
            case "minecraft:redstone_blocks":
                return CreativeModeTab.TAB_REDSTONE;
            case "minecraft:tools_and_utilities":
                return CreativeModeTab.TAB_TOOLS;
            case "minecraft:combat":
                return CreativeModeTab.TAB_COMBAT;
            case "minecraft:food_and_drinks":
                return CreativeModeTab.TAB_FOOD;
            case "minecraft:ingredients":
            case "minecraft:spawn_eggs":
                return CreativeModeTab.TAB_MISC;
            default:
                LOG.warn("Conductor content: creative tab {} has no Minecraft 1.16.1 counterpart; {} is not listed", id, what);
                return null;
        }
    }

    /**
     * 1.16 decides the right tool and map colour by material, so it follows the declared sound (stone-like blocks
     * want a pickaxe); shapes vanilla gives their own material keep it.
     */
    private static Material material(BlockDeclaration d) {
        switch (d.shape()) {
            case LEAVES:
                return Material.LEAVES;
            case CARPET:
                return Material.CLOTH_DECORATION;
            case BUTTON:
                return Material.DECORATION;
            case DOOR:
            case TRAPDOOR:
            case PRESSURE_PLATE:
            case FENCE_GATE:
                return byMaterial(d.material());
            default:
                break;
        }
        switch (d.sound()) {
            case METAL:
                return Material.METAL;
            case WOOD:
                return Material.WOOD;
            case GLASS:
                return Material.GLASS;
            case GRAVEL:
            case SAND:
                return Material.SAND;
            case GRASS:
                return Material.GRASS;
            case WOOL:
                return Material.WOOL;
            case STONE:
            case AMETHYST:
            default:
                return Material.STONE;
        }
    }

    private static Material byMaterial(BlockMaterial m) {
        switch (m) {
            case WOOD:
                return Material.WOOD;
            case IRON:
            case COPPER:
                return Material.METAL;
            case STONE:
            default:
                return Material.STONE;
        }
    }

    /** The vanilla block class for the declared shape, so the block behaves like vanilla's of that kind. */
    private static Block create(BlockDeclaration d, BlockBehaviour.Properties props) {
        BlockShape shape = d.shape();
        if (d.layer() != RenderLayer.SOLID || shape == BlockShape.GLASS || shape == BlockShape.LEAVES || shape == BlockShape.MODEL) {
            props.noOcclusion(); // see-through: neighbours' faces must still be drawn
        }
        switch (shape) {
            case CUBE:
                return new Block(props);
            case PILLAR:
                return new RotatedPillarBlock(props);
            case STAIRS: {
                String baseId = d.base().orElse("minecraft:stone");
                Block base = Registry.BLOCK.get(new ResourceLocation(baseId));
                if (base == Blocks.AIR) {
                    LOG.warn("Conductor content: stairs {} copy {}, which does not exist here; using stone", d.id(), baseId);
                    base = Blocks.STONE;
                }
                return new StairBlock(base.defaultBlockState(), props) {};
            }
            case SLAB:
                return new SlabBlock(props);
            case WALL:
                return new WallBlock(props);
            case FENCE:
                return new FenceBlock(props);
            case FENCE_GATE:
                return new FenceGateBlock(props);
            case DOOR:
                return new DoorBlock(props.noOcclusion()) {};
            case TRAPDOOR:
                return new TrapDoorBlock(props.noOcclusion()) {};
            case BUTTON:
                return d.material() == BlockMaterial.WOOD ? new WoodButtonBlock(props.noCollission()) {}
                        : new StoneButtonBlock(props.noCollission()) {};
            case PRESSURE_PLATE:
                return new PressurePlateBlock(d.material() == BlockMaterial.WOOD ? PressurePlateBlock.Sensitivity.EVERYTHING
                        : PressurePlateBlock.Sensitivity.MOBS, props.noCollission()) {};
            case PANE:
                return new IronBarsBlock(props) {};
            case LEAVES:
                return new LeavesBlock(props.randomTicks().isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                        .isValidSpawn((s, l, p, t) -> false));
            case CARPET:
                return new WoolCarpetBlock(DyeColor.WHITE, props) {};
            case GLASS:
                return new GlassBlock(props.isRedstoneConductor((s, l, p) -> false)
                        .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
            case FACING:
                return new ShapedBlocks.Facing(props);
            case HORIZONTAL_FACING:
                return new ShapedBlocks.HorizontalFacing(props);
            case MODEL:
                return new ShapedBlocks.Model(props.isRedstoneConductor((s, l, p) -> false)
                        .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false), d.boxes());
            default:
                throw new IllegalArgumentException("no Minecraft 1.16.1 block class for shape " + shape);
        }
    }

    /** From the hook in {@code DefaultAttributes}: a declared mob's attributes, or null for any other type. */
    public static AttributeSupplier mobAttributes(EntityType<?> type) {
        if (MOBS.isEmpty()) {
            return null;
        }
        MobDeclaration d = MOBS.get(type);
        if (d == null) {
            return null;
        }
        return MOB_ATTRIBUTES.computeIfAbsent(type, t -> {
            AttributeSupplier.Builder attributes;
            switch (d.archetype()) {
                case ZOMBIE:
                    attributes = Zombie.createAttributes();
                    break;
                case SPIDER:
                    attributes = Spider.createAttributes();
                    break;
                case CUSTOM:
                    // a monster's set (it has attack damage, so melee always works), with a zombie's numbers
                    attributes = Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25)
                            .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ATTACK_DAMAGE, 3);
                    break;
                default:
                    attributes = Pig.createAttributes();
                    break;
            }
            d.health().ifPresent(v -> attributes.add(Attributes.MAX_HEALTH, v));
            d.speed().ifPresent(v -> attributes.add(Attributes.MOVEMENT_SPEED, v));
            d.attackDamage().ifPresent(v -> attributes.add(Attributes.ATTACK_DAMAGE, v));
            d.armor().ifPresent(v -> attributes.add(Attributes.ARMOR, v));
            d.knockbackResistance().ifPresent(v -> attributes.add(Attributes.KNOCKBACK_RESISTANCE, v));
            d.followRange().ifPresent(v -> attributes.add(Attributes.FOLLOW_RANGE, v));
            return attributes.build();
        });
    }

    /** The declaration a mob type was registered from, or null. */
    static MobDeclaration declaration(EntityType<?> type) {
        return MOBS.get(type);
    }

    /** Whether {@code type} is a declared mob (cheap: builds nothing). */
    public static boolean isMob(EntityType<?> type) {
        return !MOBS.isEmpty() && MOBS.containsKey(type);
    }

    /** Client only: declared mobs, for their renderers. */
    public static Map<EntityType<?>, MobDeclaration> mobs() {
        return Collections.unmodifiableMap(MOBS);
    }

    /** Client only: blocks declared with a see-through render layer. */
    public static Map<Block, RenderLayer> renderLayers() {
        return Collections.unmodifiableMap(RENDER_LAYERS);
    }

    private static SoundType sounds(conductor.contract.BlockSounds s) {
        return new SoundType(s.volume(), s.pitch(), soundEvent(s.breakSound()), soundEvent(s.step()), soundEvent(s.place()),
                soundEvent(s.hit()), soundEvent(s.fall()));
    }

    static SoundEvent soundEvent(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        SoundEvent known = Registry.SOUND_EVENT.get(rl);
        return known != null ? known : new SoundEvent(rl); // the client finds sounds by id
    }

    private static SoundType sound(conductor.contract.BlockSound sound) {
        switch (sound) {
            case WOOD:
                return SoundType.WOOD;
            case METAL:
                return SoundType.METAL;
            case GLASS:
                return SoundType.GLASS;
            case GRAVEL:
                return SoundType.GRAVEL;
            case GRASS:
                return SoundType.GRASS;
            case SAND:
                return SoundType.SAND;
            case WOOL:
                return SoundType.WOOL;
            case STONE:
            case AMETHYST: // amethyst arrived in 1.17
            default:
                return SoundType.STONE;
        }
    }

    /**
     * From the hooks at the end of the built-in pack sources: one required (always on) pack per content module jar,
     * holding its {@code assets/} or {@code data/} translated for 1.16.1.
     */
    public static <T extends Pack> void loadPacks(PackType type, Consumer<T> add, Pack.PackConstructor<T> constructor) {
        for (ContentPlan.Entry entry : ContentHooks.plan().entries()) {
            Path jar = ContentHooks.jars().get(entry.moduleId());
            if (jar == null || !Files.isRegularFile(jar)) {
                continue;
            }
            try {
                Map<String, byte[]> files = PACKS.computeIfAbsent(jar + "|" + type, k -> translated(jar, type, entry.moduleId()));
                TranslatedPack pack = new TranslatedPack(jar.toFile(), files);
                T made = constructor.create("conductor/" + entry.namespace(), true, () -> new TranslatedPack(jar.toFile(), files), pack,
                        new PackMetadataSection(new TextComponent("Content from " + entry.moduleId()), PACK_FORMAT),
                        Pack.Position.TOP, PackSource.BUILT_IN);
                if (made != null) {
                    add.accept(made);
                }
            } catch (RuntimeException | LinkageError e) {
                LOG.warn("Conductor content: no {} pack for {}: {}", type, entry.moduleId(), e.toString());
            }
        }
    }

    private static Map<String, byte[]> translated(Path jar, PackType type, String moduleId) {
        Map<String, byte[]> raw = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                if (name.startsWith("assets/") || name.startsWith("data/") || name.startsWith(LegacyPackTranslator.OVERLAY)) {
                    try (InputStream in = zip.getInputStream(e)) {
                        raw.put(name, in.readAllBytes());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + jar + ": " + e, e);
        }
        LegacyPackTranslator.Result result = LegacyPackTranslator.translate(raw, type.getDirectory());
        result.notes().forEach(n -> LOG.warn("Conductor content: {} on Minecraft 1.16.1: {}", moduleId, n));
        Map<String, byte[]> files = new HashMap<>(result.files());
        files.put("pack.mcmeta", ("{\"pack\":{\"pack_format\":" + PACK_FORMAT + ",\"description\":\"Content from " + moduleId
                + "\"}}").getBytes(StandardCharsets.UTF_8));
        LOG.info("Conductor content: {} pack for {}: {} file(s) translated for Minecraft 1.16.1, {} note(s)", type.getDirectory(),
                moduleId, result.files().size(), result.notes().size());
        return Collections.unmodifiableMap(files);
    }
}
