package conductor.contract;

import java.util.Optional;

/**
 * A block a {@link ContentModule} adds, with the blockstates, models and textures from the module's assets.
 *
 * @param hardness   how long it takes to break (stone 1.5, iron block 5); -1 for unbreakable
 * @param resistance blast resistance (stone 6)
 * @param light      light level it gives off, 0-15
 * @param withItem   whether it has an item of the same id, so it can be held and placed
 * @param tab        the creative tab its item is listed in
 * @param shape      the kind of block
 * @param base       for STAIRS: the block whose look and behaviour they copy (vanilla's stairs have one too)
 * @param material   for doors, trapdoors, buttons, pressure plates and fence gates
 * @param layer      how its textures are drawn; see {@link #layer()} for the defaults
 */
public record BlockDeclaration(String id, float hardness, float resistance, int light, BlockSound sound,
                               boolean requiresTool, boolean withItem, Optional<String> tab, BlockShape shape,
                               Optional<String> base, BlockMaterial material, RenderLayer layer) {

    /** A plain cube with default material and layer: the declaration before shapes existed. */
    public BlockDeclaration(String id, float hardness, float resistance, int light, BlockSound sound, boolean requiresTool,
                            boolean withItem, Optional<String> tab) {
        this(id, hardness, resistance, light, sound, requiresTool, withItem, tab, BlockShape.CUBE, Optional.empty(),
                BlockMaterial.STONE, RenderLayer.SOLID);
    }

    /** Shapes vanilla draws with see-through pixels: they default to CUTOUT. */
    static RenderLayer defaultLayer(BlockShape shape) {
        return switch (shape) {
            case DOOR, TRAPDOOR, PANE, LEAVES, GLASS -> RenderLayer.CUTOUT;
            default -> RenderLayer.SOLID;
        };
    }

    /** Collects a block declaration; see {@link ContentDeclarations#block}. */
    public static final class Builder {
        final String id;
        float hardness = 1.5f;
        float resistance = 6f;
        int light;
        BlockSound sound = BlockSound.STONE;
        boolean requiresTool;
        boolean withItem = true;
        String tab;
        BlockShape shape = BlockShape.CUBE;
        String base;
        BlockMaterial material = BlockMaterial.STONE;
        RenderLayer layer;

        Builder(String id) {
            this.id = id;
        }

        public Builder strength(float hardness, float resistance) {
            this.hardness = hardness;
            this.resistance = resistance;
            return this;
        }

        public Builder light(int level) {
            this.light = level;
            return this;
        }

        public Builder sound(BlockSound sound) {
            this.sound = sound;
            return this;
        }

        /** Drops only when mined with the right tool (which tool comes from the {@code mineable/*} tags in data). */
        public Builder requiresTool() {
            this.requiresTool = true;
            return this;
        }

        /** No block item: the block can't be held (placed only by commands or worldgen). */
        public Builder noItem() {
            this.withItem = false;
            return this;
        }

        public Builder tab(String tabId) {
            this.tab = tabId;
            return this;
        }

        public Builder shape(BlockShape shape) {
            this.shape = shape;
            return this;
        }

        /**
         * Stairs made from another block, e.g. {@code "ruby_block"} (this module, declared earlier) or
         * {@code "minecraft:stone"}. Stairs without a base copy stone.
         */
        public Builder stairsOf(String baseBlock) {
            this.shape = BlockShape.STAIRS;
            this.base = baseBlock;
            return this;
        }

        public Builder material(BlockMaterial material) {
            this.material = material;
            return this;
        }

        public Builder layer(RenderLayer layer) {
            this.layer = layer;
            return this;
        }

        BlockDeclaration build(String namespace) {
            String b = base == null ? null : base.contains(":") ? base : namespace + ":" + base;
            BlockShape s = shape;
            return new BlockDeclaration(id, hardness, resistance, light, sound, requiresTool, withItem, Optional.ofNullable(tab),
                    s, Optional.ofNullable(s == BlockShape.STAIRS && b == null ? "minecraft:stone" : b), material,
                    layer != null ? layer : s == null ? RenderLayer.SOLID : defaultLayer(s));
        }
    }
}
