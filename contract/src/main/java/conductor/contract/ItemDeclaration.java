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
public final class ItemDeclaration {
    private final String id;
    private final int maxStack;
    private final int durability;
    private final Optional<Food> food;
    private final ItemRarity rarity;
    private final boolean fireResistant;
    private final Optional<String> tab;

    public ItemDeclaration(String id, int maxStack, int durability, Optional<Food> food, ItemRarity rarity, boolean fireResistant, Optional<String> tab) {
        this.id = id;
        this.maxStack = maxStack;
        this.durability = durability;
        this.food = food;
        this.rarity = rarity;
        this.fireResistant = fireResistant;
        this.tab = tab;
    }

    public String id() { return id; }

    public int maxStack() { return maxStack; }

    public int durability() { return durability; }

    public Optional<Food> food() { return food; }

    public ItemRarity rarity() { return rarity; }

    public Optional<String> tab() { return tab; }

    public boolean fireResistant() { return fireResistant; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemDeclaration)) return false;
        ItemDeclaration other = (ItemDeclaration) o;
        return java.util.Arrays.deepEquals(new Object[] {this.id, this.maxStack, this.durability, this.food, this.rarity, this.fireResistant, this.tab}, new Object[] {other.id, other.maxStack, other.durability, other.food, other.rarity, other.fireResistant, other.tab});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.id, this.maxStack, this.durability, this.food, this.rarity, this.fireResistant, this.tab}); }

    @Override public String toString() { return "ItemDeclaration[" + "id=" + id + ", " + "maxStack=" + maxStack + ", " + "durability=" + durability + ", " + "food=" + food + ", " + "rarity=" + rarity + ", " + "fireResistant=" + fireResistant + ", " + "tab=" + tab + "]"; }


    /** Eating restores {@code nutrition} hunger points and {@code saturation} saturation. */
    public static final class Food {
        private final int nutrition;
        private final float saturation;
        private final boolean alwaysEdible;

        public Food(int nutrition, float saturation, boolean alwaysEdible) {
            this.nutrition = nutrition;
            this.saturation = saturation;
            this.alwaysEdible = alwaysEdible;
        }

        public int nutrition() { return nutrition; }

        public float saturation() { return saturation; }

        public boolean alwaysEdible() { return alwaysEdible; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Food)) return false;
            Food other = (Food) o;
            return java.util.Arrays.deepEquals(new Object[] {this.nutrition, this.saturation, this.alwaysEdible}, new Object[] {other.nutrition, other.saturation, other.alwaysEdible});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.nutrition, this.saturation, this.alwaysEdible}); }

        @Override public String toString() { return "Food[" + "nutrition=" + nutrition + ", " + "saturation=" + saturation + ", " + "alwaysEdible=" + alwaysEdible + "]"; }
    }

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
