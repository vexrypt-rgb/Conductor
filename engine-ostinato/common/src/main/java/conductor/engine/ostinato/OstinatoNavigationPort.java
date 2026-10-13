package conductor.engine.ostinato;

import baritone.api.movement.IMovementEngine;
import baritone.api.movement.MovementBackendKind;
import baritone.api.movement.MovementGoal;
import baritone.api.movement.PathResult;
import conductor.contract.NavigationGoal;
import conductor.contract.NavigationPort;
import net.minecraft.core.BlockPos;

import java.util.function.BooleanSupplier;

/**
 * Delegates to one held Ostinato {@link IMovementEngine} instance, the existing navigation boundary. Not a fresh
 * instance per call (stage1-audit B3).
 * <p>
 * Each contract call maps to exactly one engine call: {@code goTo(MovementGoal)} or {@code cancel()}. The route check
 * only reads settings and backend availability.
 */
final class OstinatoNavigationPort implements NavigationPort {

    private final IMovementEngine engine;
    private final BooleanSupplier prefersSecondaryTravel;

    /**
     * @param prefersSecondaryTravel Ostinato's own routing rule, {@code MovementBackends.preferTungstenTravel()}:
     *                               true when travel would go to Tungsten, whose stop is not observable
     *                               (stage1-audit O2)
     */
    OstinatoNavigationPort(IMovementEngine engine, BooleanSupplier prefersSecondaryTravel) {
        this.engine = engine;
        this.prefersSecondaryTravel = prefersSecondaryTravel;
    }

    @Override
    public boolean routesToObservableBackend() {
        return !prefersSecondaryTravel.getAsBoolean();
    }

    /** Ostinato routes every {@code IMovementEngine} travel request through its custom-goal process. */
    static final String OWN_CONTROLLER = "baritone.process.CustomGoalProcess";

    @Override
    public boolean isOwnController(String controller) {
        return OWN_CONTROLLER.equals(controller);
    }

    @Override
    public Delegation travel(NavigationGoal goal) {
        PathResult result = engine.goTo(toMovementGoal(goal));
        MovementBackendKind backend = result.getBackend();
        return new Delegation(result.isAccepted(), String.valueOf(backend), backend == MovementBackendKind.BARITONE,
                result.getFailure() + " " + result.getMessage());
    }

    @Override
    public void cancel() {
        // IMovementEngine.cancel(): "Stops travel on every backend and every pathing process with it" (B6).
        engine.cancel();
    }

    static MovementGoal toMovementGoal(NavigationGoal goal) {
        return switch (goal.kind()) {
            case BLOCK -> MovementGoal.block(new BlockPos(goal.x(), goal.y(), goal.z()));
            case GET_TO_BLOCK -> MovementGoal.getToBlock(new BlockPos(goal.x(), goal.y(), goal.z()));
            case NEAR -> MovementGoal.near(new BlockPos(goal.x(), goal.y(), goal.z()), goal.range());
            case XZ -> MovementGoal.xz(goal.x(), goal.z());
        };
    }
}
