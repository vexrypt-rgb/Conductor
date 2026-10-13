package conductor.contract;

import java.util.OptionalDouble;

/**
 * A mob a {@link ContentModule} adds, built on a vanilla {@link MobArchetype}. Spawn it with {@code /summon <id>},
 * spawner data or other data; its drops come from the loot table {@code <namespace>:entities/<path>}.
 *
 * @param texture  the texture, {@code namespace:textures/entity/<name>.png}, laid out like the archetype's texture
 * @param scale    drawn and hit-box size relative to the archetype (0.25 - 4)
 */
public record MobDeclaration(String id, MobArchetype archetype, String texture, float scale, OptionalDouble health,
                             OptionalDouble speed, OptionalDouble attackDamage) {

    /** Collects a mob declaration; see {@link ContentDeclarations#mob}. */
    public static final class Builder {
        final String id;
        MobArchetype archetype = MobArchetype.ZOMBIE;
        String texture;
        float scale = 1f;
        Double health;
        Double speed;
        Double attackDamage;

        Builder(String id) {
            this.id = id;
        }

        public Builder like(MobArchetype archetype) {
            this.archetype = archetype;
            return this;
        }

        /** {@code "entity/ruby_zombie"} means {@code <namespace>:textures/entity/ruby_zombie.png}. */
        public Builder texture(String path) {
            this.texture = path;
            return this;
        }

        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        /** Maximum health in half-hearts (a zombie has 20). */
        public Builder health(double health) {
            this.health = health;
            return this;
        }

        /** Movement speed attribute (a zombie has 0.23). */
        public Builder speed(double speed) {
            this.speed = speed;
            return this;
        }

        /** Melee damage in half-hearts (a zombie has 3). */
        public Builder attackDamage(double damage) {
            this.attackDamage = damage;
            return this;
        }

        MobDeclaration build(String namespace) {
            String path = id.substring(id.indexOf(':') + 1);
            String tex = texture == null ? "entity/" + path : texture;
            return new MobDeclaration(id, archetype, tex.contains(":") ? tex : namespace + ":textures/" + tex + ".png", scale,
                    health == null ? OptionalDouble.empty() : OptionalDouble.of(health),
                    speed == null ? OptionalDouble.empty() : OptionalDouble.of(speed),
                    attackDamage == null ? OptionalDouble.empty() : OptionalDouble.of(attackDamage));
        }
    }
}
