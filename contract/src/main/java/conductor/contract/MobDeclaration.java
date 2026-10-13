package conductor.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A mob a {@link ContentModule} adds. Spawn it with {@code /summon <id>}, spawner data or other data; its drops come
 * from the loot table {@code <namespace>:entities/<path>}.
 * <p>
 * Either built on a vanilla {@link MobArchetype} ({@link Builder#like}), or fully the module's own
 * ({@link Builder#model}): its model and animations from the jar's {@code assets/}, its AI from {@link MobGoal goals},
 * its own code in a {@link MobBehaviour}, and a {@link BossBar} if it is a boss. See {@link Custom}.
 *
 * @param texture  the texture, {@code namespace:textures/entity/<name>.png}, laid out like the archetype's texture
 *                 (for a custom mob: like its model's UV layout)
 * @param scale    drawn and hit-box size relative to the archetype, or to the custom mob's size (0.25 - 4)
 * @param custom   present for a {@link MobArchetype#CUSTOM} mob
 */
public record MobDeclaration(String id, MobArchetype archetype, String texture, float scale, OptionalDouble health,
                             OptionalDouble speed, OptionalDouble attackDamage, OptionalDouble armor,
                             OptionalDouble knockbackResistance, OptionalDouble followRange, boolean fireImmune,
                             Optional<Custom> custom) {

    /**
     * What a custom mob is made of.
     *
     * @param model      jar path of its geometry: {@code assets/<namespace>/conductor/geo/<name>.geo.json}, in the
     *                   Bedrock geometry format Blockbench exports (format 1.12.0 and later). Box UV only. A bone named
     *                   {@code head} turns with the mob's look.
     * @param animations jar path of its animations, {@code assets/<namespace>/conductor/animations/<name>.animation.json}
     *                   (Bedrock format). Animations named {@code ...idle} and {@code ...walk} play by themselves,
     *                   {@code ...attack} when it hits in melee; others when its behaviour calls
     *                   {@link MobControl#animate}.
     * @param width      hit box width in blocks, before {@link MobDeclaration#scale()}
     * @param height     hit box height in blocks, before {@link MobDeclaration#scale()}
     * @param hostile    a monster (attacks, despawns in peaceful) or a creature
     * @param goals      its AI, in priority order; vanilla-like defaults when none are given
     */
    public record Custom(String model, Optional<String> animations, float width, float height, boolean hostile,
                         List<MobGoal> goals, Optional<MobBehaviour> behaviour, Optional<BossBar> boss, Sounds sounds) {
    }

    /** Sound ids, e.g. {@code minecraft:entity.iron_golem.hurt} or one from the module's own {@code sounds.json}. */
    public record Sounds(Optional<String> ambient, Optional<String> hurt, Optional<String> death, Optional<String> step) {
        public static final Sounds NONE = new Sounds(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Collects a mob declaration; see {@link ContentDeclarations#mob}. */
    public static final class Builder {
        final String id;
        MobArchetype archetype = MobArchetype.ZOMBIE;
        String texture;
        float scale = 1f;
        Double health;
        Double speed;
        Double attackDamage;
        Double armor;
        Double knockbackResistance;
        Double followRange;
        boolean fireImmune;
        String model;
        String animations;
        float width = 0.6f;
        float height = 1.8f;
        boolean hostile = true;
        final List<MobGoal> goals = new ArrayList<>();
        MobBehaviour behaviour;
        BossBar boss;
        Sounds sounds = Sounds.NONE;

        Builder(String id) {
            this.id = id;
        }

        public Builder like(MobArchetype archetype) {
            this.archetype = archetype;
            return this;
        }

        /**
         * Make this a fully custom mob, drawn with the model {@code assets/<namespace>/conductor/geo/<name>.geo.json}
         * from this jar. Animations, if any, are read from {@code .../conductor/animations/<name>.animation.json}
         * when {@link #animations} names the file.
         */
        public Builder model(String name) {
            this.archetype = MobArchetype.CUSTOM;
            this.model = name;
            return this;
        }

        /** The animation file, {@code assets/<namespace>/conductor/animations/<name>.animation.json}. */
        public Builder animations(String name) {
            this.animations = name;
            return this;
        }

        /** Hit box in blocks (a custom mob's default is a zombie's 0.6 x 1.8). */
        public Builder size(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /** A monster: the default for custom mobs. */
        public Builder hostile() {
            this.hostile = true;
            return this;
        }

        /** A creature: never attacks unless its goals say so, stays in peaceful. */
        public Builder passive() {
            this.hostile = false;
            return this;
        }

        /** Its AI, in priority order; replaces the defaults. */
        public Builder goals(MobGoal... goals) {
            this.goals.clear();
            this.goals.addAll(List.of(goals));
            return this;
        }

        /** Its own code, run on the server every tick. */
        public Builder behaviour(MobBehaviour behaviour) {
            this.behaviour = behaviour;
            return this;
        }

        /** Show a boss bar filled by its health to players near it; a boss is never despawned. */
        public Builder boss(BossBar.Color color, BossBar.Style style) {
            this.boss = new BossBar(color, style);
            return this;
        }

        /** Sounds; any may be null to stay silent. */
        public Builder sounds(String ambient, String hurt, String death) {
            this.sounds = new Sounds(Optional.ofNullable(ambient), Optional.ofNullable(hurt), Optional.ofNullable(death), sounds.step());
            return this;
        }

        public Builder stepSound(String step) {
            this.sounds = new Sounds(sounds.ambient(), sounds.hurt(), sounds.death(), Optional.ofNullable(step));
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

        /** Armor points (a zombie has 2, an iron golem 0, full diamond armor 20). */
        public Builder armor(double armor) {
            this.armor = armor;
            return this;
        }

        /** 0 to 1: the share of knockback it ignores (an iron golem has 1). */
        public Builder knockbackResistance(double resistance) {
            this.knockbackResistance = resistance;
            return this;
        }

        /** Blocks within which it finds and follows targets (a zombie has 35). */
        public Builder followRange(double blocks) {
            this.followRange = blocks;
            return this;
        }

        /** Not hurt by fire or lava. */
        public Builder fireImmune() {
            this.fireImmune = true;
            return this;
        }

        MobDeclaration build(String namespace) {
            String path = id.substring(id.indexOf(':') + 1);
            String tex = texture == null ? "entity/" + path : texture;
            Optional<Custom> custom = Optional.empty();
            if (archetype == MobArchetype.CUSTOM) {
                custom = Optional.of(new Custom(
                        model == null ? "" : "assets/" + namespace + "/conductor/geo/" + model + ".geo.json",
                        Optional.ofNullable(animations).map(a -> "assets/" + namespace + "/conductor/animations/" + a + ".animation.json"),
                        width, height, hostile, goals.isEmpty() ? defaultGoals(hostile) : List.copyOf(goals),
                        Optional.ofNullable(behaviour), Optional.ofNullable(boss), sounds));
            }
            return new MobDeclaration(id, archetype, tex.contains(":") ? tex : namespace + ":textures/" + tex + ".png", scale,
                    optional(health), optional(speed), optional(attackDamage), optional(armor), optional(knockbackResistance),
                    optional(followRange), fireImmune, custom);
        }

        /** Builder settings that only a custom mob uses, set on an archetype mob: a mistake worth reporting. */
        List<String> customOnly() {
            List<String> set = new ArrayList<>();
            if (archetype == MobArchetype.CUSTOM) {
                return set;
            }
            if (animations != null) set.add("animations");
            if (!goals.isEmpty()) set.add("goals");
            if (behaviour != null) set.add("behaviour");
            if (boss != null) set.add("boss");
            if (!sounds.equals(Sounds.NONE)) set.add("sounds");
            if (width != 0.6f || height != 1.8f) set.add("size");
            if (!hostile) set.add("passive");
            return set;
        }

        private static OptionalDouble optional(Double v) {
            return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
        }

        private static List<MobGoal> defaultGoals(boolean hostile) {
            return hostile
                    ? List.of(MobGoal.swim(), MobGoal.meleeAttack(1.0), MobGoal.wander(0.8), MobGoal.lookAtPlayers(8),
                    MobGoal.lookAround(), MobGoal.retaliate(), MobGoal.targetPlayers())
                    : List.of(MobGoal.swim(), MobGoal.panic(1.25), MobGoal.wander(1.0), MobGoal.lookAtPlayers(6),
                    MobGoal.lookAround());
        }
    }
}
