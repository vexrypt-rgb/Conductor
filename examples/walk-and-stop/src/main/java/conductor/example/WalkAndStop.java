package conductor.example;

import conductor.contract.BlockPoint;
import conductor.contract.Conductor;
import conductor.contract.ConductorModule;
import conductor.contract.DiscoveryResult;
import conductor.contract.FreshnessPolicy;
import conductor.contract.ModuleContext;
import conductor.contract.NavigationGoal;
import conductor.contract.ObservationKinds;
import conductor.contract.OperationId;
import conductor.contract.OperationResult;
import conductor.contract.OperationState;
import conductor.contract.ReleasePolicy;
import conductor.contract.ResourceState;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Example module. Waits until the bot is free, walks 20 blocks east, cancels after 2 seconds, and waits until
 * Conductor confirms the stop. Every step is logged, so the log reads like a walkthrough.
 * <p>
 * It imports only {@code conductor.contract}: no Minecraft, no Fabric, no Baritone. That is what makes the same jar
 * work on every Minecraft version Conductor supports.
 */
public final class WalkAndStop implements ConductorModule {

    private static final System.Logger LOG = System.getLogger("walk-and-stop");

    private enum Step { WAIT_FOR_RESOURCE, WALKING, STOPPING, DONE }

    private Step step = Step.WAIT_FOR_RESOURCE;
    private OperationId operation;
    private int ticksInStep;

    @Override
    public ReleasePolicy releasePolicy() {
        // How long the bot must look idle before Conductor calls it free. No default on purpose;
        // 20 ticks (1 s) covered every stop seen in testing. Give up proving a stop after 60 s.
        return new ReleasePolicy(OptionalLong.of(20), OptionalLong.of(1200));
    }

    @Override
    public void onReady(ModuleContext conductor) {
        DiscoveryResult discovery = conductor.discover();
        log("Conductor %s ready in %s", discovery.contractVersion(), discovery.context());
        discovery.capabilities().forEach(c -> log("  capability %s (%s): operations %s", c.id(), c.availability(), c.operations()));
    }

    @Override
    public void onTick(ModuleContext conductor) {
        ticksInStep++;
        switch (step) {
            case WAIT_FOR_RESOURCE -> {
                // A request is only granted while the bot is free, so check first.
                if (conductor.resourceStatus().state() == ResourceState.AVAILABLE) {
                    walk(conductor);
                }
            }
            case WALKING -> {
                if (ticksInStep == 40) {
                    OperationResult result = conductor.cancel(operation);
                    // CANCELLING means "cancel requested". The bot may keep moving until the engine lets go.
                    log("cancel requested: %s", result.status().map(s -> s.state()).orElse(null));
                    next(Step.STOPPING);
                }
            }
            case STOPPING -> {
                OperationState state = conductor.operationStatus(operation).status().orElseThrow().state();
                if (state == OperationState.CANCELLED) {
                    log("stop confirmed after %d ticks; bot is %s", ticksInStep, conductor.resourceStatus().state());
                    next(Step.DONE);
                } else if (state == OperationState.UNCERTAIN) {
                    log("stop could not be confirmed: %s", conductor.operationStatus(operation).status().orElseThrow().detail());
                    next(Step.DONE);
                }
            }
            case DONE -> {
            }
        }
    }

    private void walk(Conductor conductor) {
        // Observations come with a freshness rule you choose; there is no default.
        Optional<BlockPoint> here = conductor.observe(ObservationKinds.PLAYER_POSITION, FreshnessPolicy.sameTick())
                .observation().flatMap(o -> o.observedValue());
        if (here.isEmpty()) {
            return; // position not available this tick; try again next tick
        }
        BlockPoint target = here.get().offset(20, 0, 0);
        OperationResult result = conductor.requestTravel(NavigationGoal.getToBlock(target.x(), target.y(), target.z()));
        if (!result.isAccepted()) {
            // Every refusal says why, e.g. RESOURCE_NOT_RELEASED while another process is steering.
            log("travel refused: %s (%s)", result.rejection().orElseThrow(), result.detail());
            return; // try again next tick
        }
        operation = result.status().orElseThrow().id();
        log("walking from %s to %s: operation %d is %s", here.get(), target, operation.id(), result.status().orElseThrow().state());
        next(Step.WALKING);
    }

    private void next(Step nextStep) {
        step = nextStep;
        ticksInStep = 0;
    }

    private static void log(String format, Object... args) {
        LOG.log(System.Logger.Level.INFO, String.format(format, args));
    }
}
