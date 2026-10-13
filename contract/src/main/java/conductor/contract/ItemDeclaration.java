package conductor.contract;

import java.util.Optional;

/**
 * An item a {@link ContentModule} adds.
 *
 * @param id          {@code namespace:path}
 * @param maxStack    1-99
 * @param durability  0 for none; an item with durability stacks to 1
 * @param tab         the creative tab it is listed in, e.g. {@code minecraft:ingredients}
 */
public record ItemDeclaration(String id, int maxStack, int durability, Optional<Food> food, ItemRarity rarity,
                              boolean fireResistant, Optional<String> tab) {

    /** Eating restores {@code nutrition} hunger points and {@code saturation} saturation. */
    public record Food(int nutrition, float saturation, boolean alwaysEdible) {}

    /** Collects an item declaration; see {@link ContentDeclarations#item}. */
    public static final class Builder {
        final String id;
        int maxStack = 64;
        int durability;
        Food food;
        ItemRarity rarity = ItemRarity.COMMON;
        boolean fireResistant;
        String tab;

        Builder(String id) {
            this.id = id;
        }

        public Builder maxStack(int n) {
            this.maxStack = n;
            return this;
        }

        public Builder durability(int uses) {
            this.durability = uses;
            return this;
        }

        public Builder food(int nutrition, float saturation) {
            return food(nutrition, saturation, false);
        }

        public Builder food(int nutrition, float saturation, boolean alwaysEdible) {
            this.food = new Food(nutrition, saturation, alwaysEdible);
            return this;
        }

        public Builder rarity(ItemRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder fireResistant() {
            this.fireResistant = true;
            return this;
        }

        /** List it in this creative tab, e.g. {@code minecraft:ingredients}. */
        public Builder tab(String tabId) {
            this.tab = tabId;
            return this;
        }

        ItemDeclaration build() {
            return new ItemDeclaration(id, durability > 0 ? 1 : maxStack, durability, Optional.ofNullable(food), rarity,
                    fireResistant, Optional.ofNullable(tab));
        }
    }
}
