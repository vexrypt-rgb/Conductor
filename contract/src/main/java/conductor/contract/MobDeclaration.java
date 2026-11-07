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
public final class MobDeclaration {
    private final String id;
    private final MobArchetype archetype;
    private final String texture;
    private final float scale;
    private final OptionalDouble health;
    private final OptionalDouble speed;
    private final OptionalDouble attackDamage;
    private final OptionalDouble armor;
    private final OptionalDouble knockbackResistance;
    private final OptionalDouble followRange;
    private final boolean fireImmune;
    private final Optional<Custom> custom;

    public MobDeclaration(String id, MobArchetype archetype, String texture, float scale, OptionalDouble health, OptionalDouble speed, OptionalDouble attackDamage, OptionalDouble armor, OptionalDouble knockbackResistance, OptionalDouble followRange, boolean fireImmune, Optional<Custom> custom) {
        this.id = id;
        this.archetype = archetype;
        this.texture = texture;
        this.scale = scale;
        this.health = health;
        this.speed = speed;
        this.attackDamage = attackDamage;
        this.armor = armor;
        this.knockbackResistance = knockbackResistance;
        this.followRange = followRange;
        this.fireImmune = fireImmune;
        this.custom = custom;
    }

    public String id() { return id; }

    public MobArchetype archetype() { return archetype; }

    public String texture() { return texture; }

    public float scale() { return scale; }

    public OptionalDouble health() { return health; }

    public OptionalDouble speed() { return speed; }

    public OptionalDouble attackDamage() { return attackDamage; }

    public OptionalDouble armor() { return armor; }

    public OptionalDouble knockbackResistance() { return knockbackResistance; }

    public OptionalDouble followRange() { return followRange; }

    public Optional<Custom> custom() { return custom; }

    public boolean fireImmune() { return fireImmune; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MobDeclaration)) return false;
        MobDeclaration other = (MobDeclaration) o;
        return java.util.Arrays.deepEquals(new Object[] {this.id, this.archetype, this.texture, this.scale, this.health, this.speed, this.attackDamage, this.armor, this.knockbackResistance, this.followRange, this.fireImmune, this.custom}, new Object[] {other.id, other.archetype, other.texture, other.scale, other.health, other.speed, other.attackDamage, other.armor, other.knockbackResistance, other.followRange, other.fireImmune, other.custom});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.id, this.archetype, this.texture, this.scale, this.health, this.speed, this.attackDamage, this.armor, this.knockbackResistance, this.followRange, this.fireImmune, this.custom}); }

    @Override public String toString() { return "MobDeclaration[" + "id=" + id + ", " + "archetype=" + archetype + ", " + "texture=" + texture + ", " + "scale=" + scale + ", " + "health=" + health + ", " + "speed=" + speed + ", " + "attackDamage=" + attackDamage + ", " + "armor=" + armor + ", " + "knockbackResistance=" + knockbackResistance + ", " + "followRange=" + followRange + ", " + "fireImmune=" + fireImmune + ", " + "custom=" + custom + "]"; }


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
    public static final class Custom {
        private final String model;
        private final Optional<String> animations;
        private final float width;
        private final float height;
        private final boolean hostile;
        private final List<MobGoal> goals;
        private final Optional<MobBehaviour> behaviour;
        private final Optional<BossBar> boss;
        private final Sounds sounds;

        public Custom(String model, Optional<String> animations, float width, float height, boolean hostile, List<MobGoal> goals, Optional<MobBehaviour> behaviour, Optional<BossBar> boss, Sounds sounds) {
            this.model = model;
            this.animations = animations;
            this.width = width;
            this.height = height;
            this.hostile = hostile;
            this.goals = goals;
            this.behaviour = behaviour;
            this.boss = boss;
            this.sounds = sounds;
        }

        public String model() { return model; }

        public Optional<String> animations() { return animations; }

        public float width() { return width; }

        public float height() { return height; }

        public boolean hostile() { return hostile; }

        public List<MobGoal> goals() { return goals; }

        public Optional<MobBehaviour> behaviour() { return behaviour; }

        public Optional<BossBar> boss() { return boss; }

        public Sounds sounds() { return sounds; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Custom)) return false;
            Custom other = (Custom) o;
            return java.util.Arrays.deepEquals(new Object[] {this.model, this.animations, this.width, this.height, this.hostile, this.goals, this.behaviour, this.boss, this.sounds}, new Object[] {other.model, other.animations, other.width, other.height, other.hostile, other.goals, other.behaviour, other.boss, other.sounds});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.model, this.animations, this.width, this.height, this.hostile, this.goals, this.behaviour, this.boss, this.sounds}); }

        @Override public String toString() { return "Custom[" + "model=" + model + ", " + "animations=" + animations + ", " + "width=" + width + ", " + "height=" + height + ", " + "hostile=" + hostile + ", " + "goals=" + goals + ", " + "behaviour=" + behaviour + ", " + "boss=" + boss + ", " + "sounds=" + sounds + "]"; }
    }

    /** Sound ids, e.g. {@code minecraft:entity.iron_golem.hurt} or one from the module's own {@code sounds.json}. */
    public static final class Sounds {
        private final Optional<String> ambient;
        private final Optional<String> hurt;
        private final Optional<String> death;
        private final Optional<String> step;

        public Sounds(Optional<String> ambient, Optional<String> hurt, Optional<String> death, Optional<String> step) {
            this.ambient = ambient;
            this.hurt = hurt;
            this.death = death;
            this.step = step;
        }

        public Optional<String> ambient() { return ambient; }

        public Optional<String> hurt() { return hurt; }

        public Optional<String> death() { return death; }

        public Optional<String> step() { return step; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Sounds)) return false;
            Sounds other = (Sounds) o;
            return java.util.Arrays.deepEquals(new Object[] {this.ambient, this.hurt, this.death, this.step}, new Object[] {other.ambient, other.hurt, other.death, other.step});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.ambient, this.hurt, this.death, this.step}); }

        @Override public String toString() { return "Sounds[" + "ambient=" + ambient + ", " + "hurt=" + hurt + ", " + "death=" + death + ", " + "step=" + step + "]"; }

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
            this.goals.addAll(Jdk8.listOf(goals));
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
                        width, height, hostile, goals.isEmpty() ? defaultGoals(hostile) : Jdk8.copyList(goals),
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
                    ? Jdk8.listOf(MobGoal.swim(), MobGoal.meleeAttack(1.0), MobGoal.wander(0.8), MobGoal.lookAtPlayers(8),
                    MobGoal.lookAround(), MobGoal.retaliate(), MobGoal.targetPlayers())
                    : Jdk8.listOf(MobGoal.swim(), MobGoal.panic(1.25), MobGoal.wander(1.0), MobGoal.lookAtPlayers(6),
                    MobGoal.lookAround());
        }
    }
}
