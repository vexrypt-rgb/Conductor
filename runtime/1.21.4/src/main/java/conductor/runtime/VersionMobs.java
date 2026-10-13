package conductor.runtime;

import conductor.contract.MobDeclaration;
import conductor.contract.MobProjectile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** The mob classes and the calls around them that differ by version, for Minecraft 1.21.4 (several moved packages in 1.21.11). */
final class VersionMobs {

    private VersionMobs() {}

    /** A new entity type: the archetype's class with its hit box scaled (sizes are vanilla's), or a custom mob. */
    static EntityType<? extends Mob> type(MobDeclaration d, ResourceKey<EntityType<?>> key) {
        float scale = d.scale();
        EntityType.Builder<? extends Mob> builder = switch (d.archetype()) {
            case ZOMBIE -> EntityType.Builder.<Zombie>of(Zombie::new, MobCategory.MONSTER).sized(0.6f * scale, 1.95f * scale)
                    .eyeHeight(1.74f * scale).clientTrackingRange(8);
            case SPIDER -> EntityType.Builder.<Spider>of(Spider::new, MobCategory.MONSTER).sized(1.4f * scale, 0.9f * scale)
                    .eyeHeight(0.65f * scale).clientTrackingRange(8);
            case PIG -> EntityType.Builder.<Pig>of(Pig::new, MobCategory.CREATURE).sized(0.9f * scale, 0.9f * scale)
                    .clientTrackingRange(10);
            case CUSTOM -> {
                MobDeclaration.Custom c = d.custom().orElseThrow();
                yield c.hostile()
                        ? EntityType.Builder.<ConductorMob>of(ConductorMob.Hostile::new, MobCategory.MONSTER)
                                .sized(c.width() * scale, c.height() * scale).clientTrackingRange(10)
                        : EntityType.Builder.<ConductorMob>of(ConductorMob::new, MobCategory.CREATURE)
                                .sized(c.width() * scale, c.height() * scale).clientTrackingRange(10);
            }
        };
        if (d.fireImmune()) {
            builder.fireImmune();
        }
        return builder.build(key);
    }

    static AttributeSupplier.Builder attributes(MobDeclaration d) {
        return switch (d.archetype()) {
            case ZOMBIE -> Zombie.createAttributes();
            case SPIDER -> Spider.createAttributes();
            case PIG -> Pig.createAttributes();
            // a monster's set (it has attack damage, so melee always works), with a zombie's numbers
            case CUSTOM -> Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25)
                    .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ATTACK_DAMAGE, 3);
        };
    }

    static ServerBossEvent bossEvent(UUID id, Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        return new ServerBossEvent(name, color, overlay);
    }

    /** A projectile shot by {@code shooter} toward {@code direction}; the caller aims and adds it. */
    static Projectile projectile(MobProjectile kind, LivingEntity shooter, Vec3 direction) {
        return switch (kind) {
            case ARROW -> {
                Arrow arrow = new Arrow(shooter.level(), shooter, new ItemStack(Items.ARROW), null);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                yield arrow;
            }
            case SMALL_FIREBALL -> new SmallFireball(shooter.level(), shooter, direction);
            case SNOWBALL -> new Snowball(shooter.level(), shooter, new ItemStack(Items.SNOWBALL));
        };
    }

    /** Send an entity's new velocity to clients now (a pushed player otherwise doesn't move). */
    static void syncVelocity(Entity e) {
        e.hurtMarked = true;
    }
}
