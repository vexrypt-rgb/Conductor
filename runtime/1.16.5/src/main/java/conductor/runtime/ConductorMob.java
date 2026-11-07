package conductor.runtime;

import conductor.contract.BossBar;
import conductor.contract.EntityInfo;
import conductor.contract.MobBehaviour;
import conductor.contract.MobControl;
import conductor.contract.MobDeclaration;
import conductor.contract.MobGoal;
import conductor.contract.MobProjectile;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fully custom mob ({@link conductor.contract.MobArchetype#CUSTOM}): vanilla goals from its declaration, its
 * module's {@link MobBehaviour} run every tick on the server, an optional boss bar, and animations started on the
 * server and played on every client that sees it. {@link Hostile} is the monster variant.
 * <p>
 * The behaviour never runs inside a vanilla callback: hurt and death are noticed in the mob's own tick (health went
 * down; it is dying), so they work the same on every version and a behaviour that throws cannot break damage
 * handling. A behaviour that throws is logged and switched off for its mob type.
 * <p>
 * Minecraft 1.16.5's copy of the modern class: the same behaviour, with 1.16's names for the calls that changed.
 */
public class ConductorMob extends PathfinderMob implements RangedAttackMob {

    private static final Logger LOG = LogManager.getLogger("conductor");
    private static final double MAX_RADIUS = 64;
    /** Mob types whose behaviour threw: it is not called again until the game restarts. */
    private static final Set<String> BROKEN = ConcurrentHashMap.newKeySet();

    /** The animation the server last told clients to play, and a count that changes each time (same name again). */
    static final EntityDataAccessor<String> ANIMATION = SynchedEntityData.defineId(ConductorMob.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Integer> ANIMATION_COUNT = SynchedEntityData.defineId(ConductorMob.class, EntityDataSerializers.INT);

    private final MobDeclaration declaration;
    private final MobDeclaration.Custom custom;
    private final ServerBossEvent bossEvent;
    private final Map<String, Object> memory = new HashMap<>();
    private long age;
    private float lastHealth = -1;
    private boolean diedCalled;
    /** Client: the tick count when the current triggered animation started. */
    private int animationStartTick;

    public ConductorMob(EntityType<? extends ConductorMob> type, Level level) {
        super(type, level);
        this.declaration = ContentInstaller.declaration(type);
        this.custom = declaration == null ? null : declaration.custom().orElse(null);
        BossBar boss = custom == null ? null : custom.boss().orElse(null);
        this.bossEvent = boss == null || !(level instanceof ServerLevel) ? null : new ServerBossEvent(getDisplayName(),
                BossEvent.BossBarColor.valueOf(boss.color().name()), BossEvent.BossBarOverlay.valueOf(boss.style().name()));
        if (boss != null) {
            setPersistenceRequired();
        }
    }

    /** The monster variant: an {@link Enemy}, hostile sounds, gone in peaceful. */
    public static class Hostile extends ConductorMob implements Enemy {
        public Hostile(EntityType<? extends ConductorMob> type, Level level) {
            super(type, level);
        }

        @Override
        public SoundSource getSoundSource() {
            return SoundSource.HOSTILE;
        }

        @Override
        protected boolean shouldDespawnInPeaceful() {
            return true;
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIMATION, "");
        entityData.define(ANIMATION_COUNT, 0);
    }

    @Override
    protected void registerGoals() {
        // called from Mob's constructor, before this class's fields are set: read the declaration from the type
        MobDeclaration d = ContentInstaller.declaration(getType());
        if (d == null || d.custom().isEmpty()) {
            return;
        }
        int priority = 0;
        int targetPriority = 1;
        for (MobGoal g : d.custom().get().goals()) {
            switch (g.kind()) {
                case FLOAT -> goalSelector.addGoal(priority++, new FloatGoal(this));
                case MELEE_ATTACK -> goalSelector.addGoal(priority++, new MeleeAttackGoal(this, g.speed(), true));
                case RANGED_ATTACK -> goalSelector.addGoal(priority++,
                        new RangedAttackGoal(this, g.speed(), Math.max(1, g.interval()), (float) g.range()));
                case WANDER -> goalSelector.addGoal(priority++, new WaterAvoidingRandomStrollGoal(this, g.speed()));
                case LOOK_AT_PLAYERS -> goalSelector.addGoal(priority++, new LookAtPlayerGoal(this, Player.class, (float) g.range()));
                case LOOK_AROUND -> goalSelector.addGoal(priority++, new RandomLookAroundGoal(this));
                case PANIC -> goalSelector.addGoal(priority++, new PanicGoal(this, g.speed()));
                case RETALIATE -> targetSelector.addGoal(targetPriority++, new HurtByTargetGoal(this));
                case TARGET_PLAYERS -> targetSelector.addGoal(targetPriority++, new NearestAttackableTargetGoal<>(this, Player.class, true));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    private void serverTick(ServerLevel server) {
        age++;
        float health = getHealth();
        if (bossEvent != null) {
            bossEvent.setPercent(Math.max(0, Math.min(1, health / getMaxHealth())));
        }
        MobBehaviour behaviour = custom == null ? null : custom.behaviour().orElse(null);
        if (behaviour == null || BROKEN.contains(declaration.id())) {
            lastHealth = health;
            return;
        }
        Control control = new Control(server);
        try {
            if (age == 1) {
                behaviour.spawned(control);
            }
            if (lastHealth >= 0 && health < lastHealth) {
                behaviour.hurt(control, lastHealth - health);
            }
            if (isDeadOrDying()) {
                if (!diedCalled) {
                    diedCalled = true;
                    behaviour.died(control);
                }
            } else {
                behaviour.tick(control);
            }
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError && !(t instanceof StackOverflowError)) {
                throw (VirtualMachineError) t;
            }
            BROKEN.add(declaration.id());
            LOG.error("Conductor mob {}: its behaviour threw; it is switched off for this mob type until the game restarts. "
                    + "The mobs keep their goals.", declaration.id(), t);
        } finally {
            control.closed = true;
        }
        lastHealth = getHealth();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            playAnimation("attack");
        }
        return hit;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (level instanceof ServerLevel server) {
            MobProjectile kind = MobProjectile.ARROW;
            if (custom != null) {
                for (MobGoal g : custom.goals()) {
                    if (g.kind() == MobGoal.Kind.RANGED_ATTACK) {
                        kind = g.projectile() == null ? MobProjectile.ARROW : g.projectile();
                        break;
                    }
                }
            }
            shootAt(server, target, kind);
        }
    }

    private boolean shootAt(ServerLevel server, LivingEntity target, MobProjectile kind) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        Projectile p = projectile(kind, new Vec3(dx, target.getY(0.5) - getY(0.5), dz).normalize());
        if (kind == MobProjectile.SMALL_FIREBALL) {
            p.setPos(getX(), getY(0.5) + 0.5, getZ());
        } else {
            // as a skeleton aims: a little above the target, more the farther it is
            double dy = target.getY(1.0 / 3.0) - p.getY();
            p.shoot(dx, dy + horizontal * 0.2, dz, 1.6f, 14 - server.getDifficulty().getId() * 4);
        }
        server.addFreshEntity(p);
        playAnimation("shoot");
        return true;
    }

    private Projectile projectile(MobProjectile kind, Vec3 direction) {
        switch (kind) {
            case SMALL_FIREBALL:
                return new SmallFireball(level, this, direction.x, direction.y, direction.z);
            case SNOWBALL:
                return new Snowball(level, this);
            default:
                Arrow arrow = new Arrow(level, this);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                return arrow;
        }
    }

    /** Server: tell clients to play an animation (by name or role). True if the mob has such an animation. */
    boolean playAnimation(String name) {
        CustomMobAssets.Loaded assets = declaration == null ? null : CustomMobAssets.get(declaration, ContentInstaller.MINECRAFT);
        if (assets == null || assets.animations().find(name) == null) {
            return false;
        }
        entityData.set(ANIMATION, name);
        entityData.set(ANIMATION_COUNT, entityData.get(ANIMATION_COUNT) + 1);
        return true;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ANIMATION_COUNT.equals(key)) {
            animationStartTick = tickCount;
        }
    }

    /** Client: the animation the server last started, or "" for none. */
    public String triggeredAnimation() {
        return entityData.get(ANIMATION);
    }

    /** Client: seconds since the triggered animation started. */
    public double triggeredSeconds(float partialTick) {
        return (tickCount - animationStartTick + partialTick) / 20.0;
    }

    /** The declaration this mob was made from (null only if its type was not registered by Conductor). */
    public MobDeclaration declaration() {
        return declaration;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (bossEvent != null) {
            bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bossEvent != null) {
            bossEvent.removePlayer(player);
        }
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        if (bossEvent != null) {
            bossEvent.setName(getDisplayName());
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return bossEvent == null && custom != null && custom.hostile() && super.removeWhenFarAway(distance);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return sound(custom == null ? Optional.empty() : custom.sounds().ambient());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return sound(custom == null ? Optional.empty() : custom.sounds().hurt());
    }

    @Override
    protected SoundEvent getDeathSound() {
        return sound(custom == null ? Optional.empty() : custom.sounds().death());
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundEvent step = sound(custom == null ? Optional.empty() : custom.sounds().step());
        if (step == null) {
            super.playStepSound(pos, state);
        } else {
            playSound(step, 0.15f, 1f);
        }
    }

    private static SoundEvent sound(Optional<String> id) {
        return id.map(ContentInstaller::soundEvent).orElse(null);
    }

    /** The behaviour's view of this mob during one call; refuses everything once the call is over. */
    private final class Control implements MobControl {
        private final ServerLevel server;
        private boolean closed;

        Control(ServerLevel server) {
            this.server = server;
        }

        private void check() {
            if (closed) {
                throw new IllegalStateException("a MobControl was kept after its call");
            }
        }

        @Override
        public String type() {
            return declaration.id();
        }

        @Override
        public int entityId() {
            return getId();
        }

        @Override
        public double x() {
            return getX();
        }

        @Override
        public double y() {
            return getY();
        }

        @Override
        public double z() {
            return getZ();
        }

        @Override
        public double health() {
            return getHealth();
        }

        @Override
        public double maxHealth() {
            return getMaxHealth();
        }

        @Override
        public long age() {
            return age;
        }

        @Override
        public boolean onGround() {
            return isOnGround();
        }

        @Override
        public Optional<EntityInfo> target() {
            check();
            LivingEntity t = getTarget();
            return t == null || !t.isAlive() ? Optional.empty() : Optional.of(info(t));
        }

        @Override
        public List<EntityInfo> playersWithin(double radius) {
            check();
            double r = cap(radius);
            List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer p : server.players()) {
                if (!p.isSpectator() && p.isAlive() && p.distanceTo(ConductorMob.this) <= r) {
                    near.add(p);
                }
            }
            near.sort(Comparator.comparingDouble(p -> p.distanceTo(ConductorMob.this)));
            return near.stream().map(this::info).collect(java.util.stream.Collectors.toList());
        }

        private EntityInfo info(LivingEntity e) {
            return new EntityInfo(e.getId(), Registry.ENTITY_TYPE.getKey(e.getType()).toString(), e.getName().getString(),
                    e.getX(), e.getY(), e.getZ(), e.distanceTo(ConductorMob.this), OptionalDouble.of(e.getHealth()),
                    e instanceof Player, e instanceof Enemy);
        }

        @Override
        public Map<String, Object> memory() {
            check();
            return memory;
        }

        @Override
        public boolean animate(String animation) {
            check();
            return playAnimation(animation);
        }

        @Override
        public boolean shoot(MobProjectile projectile) {
            check();
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && projectile != null && shootAt(server, t, projectile);
        }

        @Override
        public boolean leap(double horizontal, double vertical) {
            check();
            LivingEntity t = getTarget();
            if (t == null) {
                return false;
            }
            Vec3 d = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
            d = d.lengthSqr() < 1e-7 ? Vec3.ZERO : d.normalize().scale(clamp(horizontal, 0, 4));
            setDeltaMovement(d.x, clamp(vertical, -4, 4), d.z);
            return true;
        }

        /** Who an area effect reaches: not this mob, its own type, spectators, or (for a hostile mob) other monsters. */
        private List<LivingEntity> others(double radius) {
            double r = cap(radius);
            return server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r),
                    e -> e != ConductorMob.this && e.isAlive() && e.getType() != getType() && !e.isSpectator()
                            && !(ConductorMob.this instanceof Enemy && e instanceof Enemy)
                            && e.distanceTo(ConductorMob.this) <= r);
        }

        @Override
        public int damageNearby(double radius, double amount) {
            check();
            if (!(amount > 0)) {
                return 0;
            }
            List<LivingEntity> hit = others(radius);
            DamageSource source = DamageSource.mobAttack(ConductorMob.this);
            for (LivingEntity e : hit) {
                e.hurt(source, (float) Math.min(amount, 1000));
            }
            return hit.size();
        }

        @Override
        public int pushNearby(double radius, double strength) {
            check();
            double s = clamp(strength, 0, 4);
            List<LivingEntity> pushed = others(radius);
            for (LivingEntity e : pushed) {
                Vec3 d = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
                d = d.lengthSqr() < 1e-7 ? new Vec3(0, 0, 0) : d.normalize().scale(s);
                e.push(d.x, Math.min(0.5, s * 0.25), d.z);
                e.hurtMarked = true;
            }
            return pushed.size();
        }

        @Override
        public int effectNearby(double radius, String effect, int seconds, int amplifier) {
            check();
            ResourceLocation id = effect == null ? null : ResourceLocation.tryParse(effect);
            var holder = id == null ? java.util.Optional.<net.minecraft.world.effect.MobEffect>empty() : Registry.MOB_EFFECT.getOptional(id);
            if (holder.isEmpty() || seconds <= 0) {
                return 0;
            }
            List<LivingEntity> hit = others(radius);
            for (LivingEntity e : hit) {
                e.addEffect(new MobEffectInstance(holder.get(), Math.min(seconds, 3600) * 20, clamp(amplifier, 0, 9)));
            }
            return hit.size();
        }

        @Override
        public boolean summon(String entityType, double dx, double dy, double dz) {
            check();
            if (Math.abs(dx) > MAX_RADIUS || Math.abs(dy) > MAX_RADIUS || Math.abs(dz) > MAX_RADIUS) {
                return false;
            }
            ResourceLocation id = entityType == null ? null : ResourceLocation.tryParse(entityType);
            var type = id == null ? java.util.Optional.<EntityType<?>>empty() : Registry.ENTITY_TYPE.getOptional(id);
            if (type.isEmpty()) {
                return false;
            }
            Entity e = type.get().create(server);
            if (e == null) {
                return false;
            }
            e.setPos(getX() + dx, getY() + dy, getZ() + dz);
            e.yRot = yRot;
            if (e instanceof Mob m && getTarget() != null) {
                m.setTarget(getTarget());
            }
            return server.addFreshEntity(e);
        }

        @Override
        public boolean teleport(double x, double y, double z) {
            check();
            if (Math.abs(x - getX()) > MAX_RADIUS || Math.abs(y - getY()) > MAX_RADIUS || Math.abs(z - getZ()) > MAX_RADIUS) {
                return false;
            }
            teleportTo(x, y, z);
            return true;
        }

        @Override
        public void heal(double amount) {
            check();
            if (amount > 0) {
                ConductorMob.this.heal((float) amount);
            }
        }

        @Override
        public void sound(String sound, float volume, float pitch) {
            check();
            server.playSound((Player) null, getX(), getY(), getZ(), ContentInstaller.soundEvent(sound), getSoundSource(),
                    Math.max(0, Math.min(volume, 16)), Math.max(0.5f, Math.min(pitch, 2)));
        }

        @Override
        public void particles(String particle, int count, double spread) {
            check();
            ResourceLocation id = particle == null ? null : ResourceLocation.tryParse(particle);
            if (id != null && Registry.PARTICLE_TYPE.get(id) instanceof SimpleParticleType p) {
                double s = clamp(spread, 0, 16);
                server.sendParticles(p, getX(), getY(0.5), getZ(), clamp(count, 0, 500), s, s, s, 0.02);
            }
        }

        @Override
        public void say(String message, double radius) {
            check();
            double r = cap(radius);
            Component text = new TextComponent(message == null ? "" : message);
            for (ServerPlayer p : server.players()) {
                if (p.distanceTo(ConductorMob.this) <= r) {
                    p.sendMessage(text, Util.NIL_UUID);
                }
            }
        }

        @Override
        public void bossBar(BossBar.Color color) {
            check();
            if (bossEvent != null && color != null) {
                bossEvent.setColor(BossEvent.BossBarColor.valueOf(color.name()));
            }
        }

        @Override
        public void log(String message) {
            LOG.info("[{}] {}", declaration.id(), message);
        }
    }

    private static double cap(double radius) {
        return clamp(radius, 0, MAX_RADIUS);
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
