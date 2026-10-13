package conductor.runtime;

import conductor.contract.MobArchetype;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.animal.pig.Pig;

/** The archetype mobs' classes for Minecraft 1.21.11 (several moved packages in 1.21.11). */
final class VersionMobs {

    private VersionMobs() {}

    /** A new entity type that uses the archetype's class, with its hit box scaled. Sizes are vanilla's. */
    static EntityType<? extends Mob> type(MobArchetype archetype, ResourceKey<EntityType<?>> key, float scale) {
        return switch (archetype) {
            case ZOMBIE -> EntityType.Builder.<Zombie>of(Zombie::new, MobCategory.MONSTER).sized(0.6f * scale, 1.95f * scale)
                    .eyeHeight(1.74f * scale).clientTrackingRange(8).build(key);
            case SPIDER -> EntityType.Builder.<Spider>of(Spider::new, MobCategory.MONSTER).sized(1.4f * scale, 0.9f * scale)
                    .eyeHeight(0.65f * scale).clientTrackingRange(8).build(key);
            case PIG -> EntityType.Builder.<Pig>of(Pig::new, MobCategory.CREATURE).sized(0.9f * scale, 0.9f * scale)
                    .clientTrackingRange(10).build(key);
        };
    }

    static AttributeSupplier.Builder attributes(MobArchetype archetype) {
        return switch (archetype) {
            case ZOMBIE -> Zombie.createAttributes();
            case SPIDER -> Spider.createAttributes();
            case PIG -> Pig.createAttributes();
        };
    }
}
