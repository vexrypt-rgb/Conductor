package conductor.runtime.ostinato;

import baritone.api.movement.IMovementEngine;
import conductor.contract.CombatGoal;
import conductor.contract.CombatPort;
import conductor.contract.NavigationPort;

import java.util.Set;

/**
 * Combat through Ostinato's own combat processes (Stage 5). Players go to {@code PvpProcess}, mobs to
 * {@code PveProcess}, exactly as Ostinato's {@code #pvp} / {@code #pve} commands route them.
 * <p>
 * Stop: the same engine-wide cancel as travel. {@code IMovementEngine.cancel()} ends in
 * {@code PathingControlManager.cancelEverything()}, which calls each process's {@code onLostControl()}; both combat
 * processes clear their target there, release the use key and clear forced inputs (Stage 5 audit). Conductor still
 * waits for the evidence.
 */
final class OstinatoCombatPort implements CombatPort {

    static final Set<String> OWN_CONTROLLERS = Set.of("baritone.process.PvpProcess", "baritone.process.PveProcess");

    private final CombatAccess combat;
    private final IMovementEngine engine;

    OstinatoCombatPort(CombatAccess combat, IMovementEngine engine) {
        this.combat = combat;
        this.engine = engine;
    }

    @Override
    public boolean isOwnController(String controller) {
        return OWN_CONTROLLERS.contains(controller);
    }

    @Override
    public NavigationPort.Delegation engage(CombatGoal goal) {
        switch (goal.kind()) {
            case PLAYER -> combat.attackPlayer(goal.name());
            case PLAYERS -> combat.attackPlayers();
            case HOSTILES -> combat.attackHostiles();
            case MOB -> combat.attackMob(goal.name());
        }
        String backend = goal.kind() == CombatGoal.Kind.PLAYER || goal.kind() == CombatGoal.Kind.PLAYERS ? "ostinato-pvp" : "ostinato-pve";
        // The processes take the goal without a verdict; whether a target exists shows up in COMBAT_TARGET.
        return new NavigationPort.Delegation(true, backend, true, "");
    }

    @Override
    public void cancel() {
        engine.cancel();
    }
}
