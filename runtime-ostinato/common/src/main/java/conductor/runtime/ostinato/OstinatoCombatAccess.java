package conductor.runtime.ostinato;

import baritone.Baritone;
import baritone.api.IBaritone;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * Ostinato's combat processes ({@code PvpProcess}, {@code PveProcess}), reached the same way Ostinato's own
 * {@code #pvp} and {@code #pve} commands reach them. Both Ostinato branches Conductor targets have these methods.
 */
final class OstinatoCombatAccess implements CombatAccess {

    private final Baritone baritone;

    OstinatoCombatAccess(IBaritone baritone) {
        this.baritone = (Baritone) baritone;
    }

    @Override
    public void attackPlayer(String name) {
        baritone.getPvpProcess().attackPlayer(name);
    }

    @Override
    public void attackPlayers() {
        baritone.getPvpProcess().attackPlayers();
    }

    @Override
    public void attackHostiles() {
        baritone.getPveProcess().attackHostiles();
    }

    @Override
    public void attackMob(String entityId) {
        baritone.getPveProcess().attackType(entityId);
    }

    @Override
    public Optional<String> target() {
        LivingEntity t = baritone.getPvpProcess().isActive() ? baritone.getPvpProcess().getTarget() : null;
        if (t == null && baritone.getPveProcess().isActive()) {
            t = baritone.getPveProcess().getTarget();
        }
        if (t == null) {
            return Optional.empty();
        }
        return Optional.of(EntityType.getKey(t.getType()) + " " + t.getName().getString());
    }
}
