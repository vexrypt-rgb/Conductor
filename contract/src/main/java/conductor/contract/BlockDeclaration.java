package conductor.contract;

import java.util.Optional;

/**
 * A block a {@link ContentModule} adds: a full cube with the model and textures from the module's assets.
 *
 * @param hardness   how long it takes to break (stone 1.5, iron block 5); -1 for unbreakable
 * @param resistance blast resistance (stone 6)
 * @param light      light level it gives off, 0-15
 * @param withItem   whether it has an item of the same id, so it can be held and placed
 * @param tab        the creative tab its item is listed in
 */
public record BlockDeclaration(String id, float hardness, float resistance, int light, BlockSound sound,
                               boolean requiresTool, boolean withItem, Optional<String> tab) {

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

        BlockDeclaration build() {
            return new BlockDeclaration(id, hardness, resistance, light, sound, requiresTool, withItem, Optional.ofNullable(tab));
        }
    }
}
