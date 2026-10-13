package conductor.example.ruby;

import conductor.contract.BossBar;
import conductor.contract.EntityInfo;
import conductor.contract.MobBehaviour;
import conductor.contract.MobControl;
import conductor.contract.MobProjectile;

import java.util.Map;
import java.util.Optional;

/**
 * The ruby golem's fight, on top of its melee goal. Close up it raises both arms and slams the ground (the hit lands
 * when the animation does); farther away it throws fireballs. At half health it is enraged: red bar, faster attacks,
 * and it calls ruby zombies, again every 20 seconds. One instance serves every golem, so all state is in
 * {@link MobControl#memory()}.
 */
final class RubyGolemBehaviour implements MobBehaviour {

    /** Ticks from the start of the slam animation to the arms hitting the ground (0.8 s). */
    private static final int SLAM_HIT = 16;

    @Override
    public void tick(MobControl mob) {
        Map<String, Object> m = mob.memory();
        long age = mob.age();
        boolean enraged = Boolean.TRUE.equals(m.get("enraged"));
        if (m.get("slamAt") instanceof Long at && at == age) {
            int hit = mob.damageNearby(4.5, enraged ? 14 : 9);
            mob.pushNearby(5, 1.5);
            mob.particles("minecraft:explosion", 6, 1.5);
            mob.sound("minecraft:entity.generic.explode", 1f, 0.7f);
            mob.log("slam hit " + hit);
        }
        Optional<EntityInfo> target = mob.target();
        if (target.isEmpty() || age < (m.get("ready") instanceof Long r ? r : 0L)) {
            return;
        }
        double distance = target.get().distance();
        if (enraged && age >= (m.get("nextSummon") instanceof Long s ? s : 0L)) {
            summonZombies(mob);
            m.put("nextSummon", age + 400);
            m.put("ready", age + 30);
        } else if (distance < 4.5 && mob.onGround()) {
            mob.animate("slam");
            m.put("slamAt", age + SLAM_HIT);
            m.put("ready", age + (enraged ? 50 : 90));
        } else if (distance > 6 && distance < 24) {
            if (mob.shoot(MobProjectile.SMALL_FIREBALL)) {
                mob.log("fireball at " + Math.round(distance) + " blocks");
            }
            m.put("ready", age + (enraged ? 25 : 50));
        }
    }

    @Override
    public void hurt(MobControl mob, double amount) {
        Map<String, Object> m = mob.memory();
        if (!Boolean.TRUE.equals(m.get("enraged")) && mob.healthFraction() <= 0.5) {
            m.put("enraged", true);
            mob.animate("enrage");
            mob.bossBar(BossBar.Color.RED);
            mob.sound("minecraft:entity.ravager.roar", 2f, 0.8f);
            mob.say("The Ruby Golem is enraged!", 48);
            m.put("nextSummon", mob.age() + 30);
            m.put("ready", mob.age() + 30);
        }
    }

    @Override
    public void died(MobControl mob) {
        mob.particles("minecraft:explosion", 12, 2);
        mob.say("The Ruby Golem shatters.", 48);
    }

    private static void summonZombies(MobControl mob) {
        mob.animate("summon");
        mob.sound("minecraft:entity.evoker.prepare_summon", 1.5f, 1f);
        mob.particles("minecraft:flame", 40, 1.5);
        mob.summon("ruby:ruby_zombie", 2, 0, 0);
        mob.summon("ruby:ruby_zombie", -2, 0, 0);
        mob.log("summoned ruby zombies");
    }
}
