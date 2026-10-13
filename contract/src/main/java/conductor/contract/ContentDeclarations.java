package conductor.contract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What one {@link ContentModule} declares. Paths are relative to the module's namespace: {@code item("ruby")} in
 * namespace {@code ruby} declares {@code ruby:ruby}. Problems are collected, not thrown: a module with any problem is
 * skipped as a whole, because registering part of it would leave client and server with different content.
 */
public final class ContentDeclarations {

    /** Minecraft's own rule for namespaces. */
    public static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    /** Minecraft's own rule for paths. */
    public static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");
    /** Namespaces a module may not use: the game's own, and ones loaders reserve. */
    public static final Set<String> RESERVED = Set.of("minecraft", "realms", "brigadier", "c", "fabric", "forge", "neoforge", "conductor");
    /** Tab ids are checked for syntax only; whether the tab exists is checked by the runtime. */
    static final Pattern TAB = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    /** More boxes than this make outline checks slow; real models have far fewer. */
    static final int MAX_BOXES = 256;
    static final Pattern TEXTURE = Pattern.compile("[a-z0-9_.-]+:textures/[a-z0-9_./-]+\\.png");

    private final String namespace;
    private final List<ItemDeclaration.Builder> items = new ArrayList<>();
    private final List<BlockDeclaration.Builder> blocks = new ArrayList<>();
    private final List<MobDeclaration.Builder> mobs = new ArrayList<>();

    public ContentDeclarations(String namespace) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
    }

    public String namespace() {
        return namespace;
    }

    public ItemDeclaration.Builder item(String path) {
        ItemDeclaration.Builder b = new ItemDeclaration.Builder(namespace + ":" + path);
        items.add(b);
        return b;
    }

    public BlockDeclaration.Builder block(String path) {
        BlockDeclaration.Builder b = new BlockDeclaration.Builder(namespace + ":" + path);
        blocks.add(b);
        return b;
    }

    /** A mob built on a vanilla archetype; see {@link MobDeclaration}. */
    public MobDeclaration.Builder mob(String path) {
        MobDeclaration.Builder b = new MobDeclaration.Builder(namespace + ":" + path);
        mobs.add(b);
        return b;
    }

    /** The checked result. */
    public record Checked(String namespace, List<ItemDeclaration> items, List<BlockDeclaration> blocks, List<MobDeclaration> mobs,
                          List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    public Checked check() {
        List<String> problems = new ArrayList<>();
        if (!NAMESPACE.matcher(namespace).matches()) {
            problems.add("namespace '" + namespace + "' must match " + NAMESPACE.pattern());
        } else if (RESERVED.contains(namespace)) {
            problems.add("namespace '" + namespace + "' is reserved");
        }
        Set<String> itemIds = new HashSet<>();
        Set<String> blockIds = new HashSet<>();
        List<BlockDeclaration> checkedBlocks = new ArrayList<>();
        for (BlockDeclaration.Builder b : blocks) {
            BlockDeclaration d = b.build(namespace);
            path(d.id(), problems);
            if (!blockIds.add(d.id())) {
                problems.add("block " + d.id() + " declared twice");
            }
            if (d.withItem() && !itemIds.add(d.id())) {
                problems.add("item " + d.id() + " declared twice");
            }
            if (!(d.hardness() == -1 || (d.hardness() >= 0 && d.hardness() <= 1_000_000))) {
                problems.add("block " + d.id() + ": hardness must be -1 (unbreakable) or 0..1000000");
            }
            if (!(d.resistance() >= 0 && d.resistance() <= 3_600_000)) {
                problems.add("block " + d.id() + ": resistance must be 0..3600000");
            }
            if (d.light() < 0 || d.light() > 15) {
                problems.add("block " + d.id() + ": light must be 0..15");
            }
            if (d.sound() == null) {
                problems.add("block " + d.id() + ": sound is null");
            }
            d.tab().ifPresent(t -> tab(d.id(), t, problems));
            if (d.tab().isPresent() && !d.withItem()) {
                problems.add("block " + d.id() + ": a creative tab needs the block item");
            }
            if (d.shape() == null || d.material() == null || d.layer() == null) {
                problems.add("block " + d.id() + ": shape, material and layer must not be null");
            }
            d.base().ifPresent(base -> {
                if (d.shape() != BlockShape.STAIRS) {
                    problems.add("block " + d.id() + ": only stairs have a base block");
                } else if (!TAB.matcher(base).matches()) {
                    problems.add("block " + d.id() + ": base '" + base + "' is not a namespace:path id");
                } else if (base.equals(d.id()) || base.startsWith(namespace + ":") && !blockIds.contains(base)) {
                    problems.add("block " + d.id() + ": base " + base + " must be declared before the stairs");
                } else if (!base.startsWith(namespace + ":") && !base.startsWith("minecraft:")) {
                    problems.add("block " + d.id() + ": base " + base + " must be this module's or a vanilla block");
                }
            });
            if (d.shape() == BlockShape.MODEL) {
                if (d.boxes().isEmpty()) {
                    problems.add("block " + d.id() + ": a MODEL block needs at least one box");
                } else if (d.boxes().size() > MAX_BOXES) {
                    problems.add("block " + d.id() + ": at most " + MAX_BOXES + " boxes");
                }
                d.boxes().stream().filter(x -> !x.valid()).findFirst().ifPresent(x -> problems.add("block " + d.id()
                        + ": box " + x + " must lie within 0..16 with each start below its end"));
            } else if (!d.boxes().isEmpty()) {
                problems.add("block " + d.id() + ": only MODEL blocks have boxes");
            }
            d.sounds().ifPresent(s -> {
                for (String sound : List.of(s.breakSound(), s.step(), s.place(), s.hit(), s.fall())) {
                    if (sound == null || !TAB.matcher(sound).matches()) {
                        problems.add("block " + d.id() + ": sound '" + sound + "' is not a namespace:path id");
                    }
                }
            });
            checkedBlocks.add(d);
        }
        List<ItemDeclaration> checkedItems = new ArrayList<>();
        for (ItemDeclaration.Builder b : items) {
            ItemDeclaration d = b.build();
            path(d.id(), problems);
            if (!itemIds.add(d.id())) {
                problems.add("item " + d.id() + " declared twice");
            }
            if (d.maxStack() < 1 || d.maxStack() > 99) {
                problems.add("item " + d.id() + ": maxStack must be 1..99");
            }
            if (d.durability() < 0) {
                problems.add("item " + d.id() + ": durability must be 0 or more");
            }
            d.food().ifPresent(f -> {
                if (f.nutrition() < 0 || !(f.saturation() >= 0)) {
                    problems.add("item " + d.id() + ": food values must be 0 or more");
                }
            });
            if (d.rarity() == null) {
                problems.add("item " + d.id() + ": rarity is null");
            }
            d.tab().ifPresent(t -> tab(d.id(), t, problems));
            checkedItems.add(d);
        }
        Set<String> mobIds = new HashSet<>();
        List<MobDeclaration> checkedMobs = new ArrayList<>();
        for (MobDeclaration.Builder b : mobs) {
            MobDeclaration d = b.build(namespace);
            path(d.id(), problems);
            if (!mobIds.add(d.id())) {
                problems.add("mob " + d.id() + " declared twice");
            }
            if (d.archetype() == null) {
                problems.add("mob " + d.id() + ": archetype is null");
            }
            if (!(d.scale() >= 0.25f && d.scale() <= 4f)) {
                problems.add("mob " + d.id() + ": scale must be 0.25..4");
            }
            if (!TEXTURE.matcher(d.texture()).matches()) {
                problems.add("mob " + d.id() + ": texture '" + d.texture() + "' must be namespace:textures/....png");
            }
            if (d.health().isPresent() && !(d.health().getAsDouble() >= 1 && d.health().getAsDouble() <= 1024)) {
                problems.add("mob " + d.id() + ": health must be 1..1024");
            }
            if (d.speed().isPresent() && !(d.speed().getAsDouble() >= 0 && d.speed().getAsDouble() <= 1)) {
                problems.add("mob " + d.id() + ": speed must be 0..1");
            }
            if (d.attackDamage().isPresent() && !(d.attackDamage().getAsDouble() >= 0 && d.attackDamage().getAsDouble() <= 2048)) {
                problems.add("mob " + d.id() + ": attack damage must be 0..2048");
            }
            checkedMobs.add(d);
        }
        return new Checked(namespace, List.copyOf(checkedItems), List.copyOf(checkedBlocks), List.copyOf(checkedMobs),
                List.copyOf(problems));
    }

    private static void path(String id, List<String> problems) {
        String path = id.substring(id.indexOf(':') + 1);
        if (!PATH.matcher(path).matches()) {
            problems.add("'" + id + "': path must match " + PATH.pattern());
        }
    }

    private static void tab(String id, String tab, List<String> problems) {
        if (!TAB.matcher(tab).matches()) {
            problems.add(id + ": creative tab '" + tab + "' is not a namespace:path id");
        }
    }
}
