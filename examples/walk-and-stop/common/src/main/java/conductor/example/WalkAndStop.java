package conductor.example;

import conductor.contract.Conductor;
import conductor.contract.ConductorModule;
import conductor.contract.DiscoveryResult;
import conductor.contract.FreshnessPolicy;
import conductor.contract.NavigationGoal;
import conductor.contract.ObservationKinds;
import conductor.contract.ObservationResult;
import conductor.contract.OperationId;
import conductor.contract.OperationResult;
import conductor.contract.OperationState;
import conductor.contract.ResourceState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example module. Waits until the movement resource is free, walks 20 blocks east, cancels after 2 seconds, and
 * waits until Conductor confirms the stop. Every step is logged, so the log reads like a walkthrough.
 * <p>
 * It uses only the Conductor contract, plus Minecraft for the player's position. Nothing here talks to Baritone.
 */
public final class WalkAndStop implements ConductorModule {

    private static final Logger LOG = LoggerFactory.getLogger("walk-and-stop");

    private enum Step { WAIT_FOR_RESOURCE, WALKING, STOPPING, DONE }

    private Step step = Step.WAIT_FOR_RESOURCE;
    private OperationId operation;
    private int ticksInStep;

    @Override
    public void onReady(Conductor conductor) {
        DiscoveryResult discovery = conductor.discover();
        LOG.info("Conductor {} ready in {}", discovery.contractVersion(), discovery.context());
        discovery.capabilities().forEach(c -> LOG.info("  capability {} ({}): operations {}", c.id(), c.availability(), c.operations()));
    }

    @Override
    public void onTick(Conductor conductor) {
        ticksInStep++;
        switch (step) {
            case WAIT_FOR_RESOURCE -> {
                // A request is only granted while the resource is AVAILABLE, so check first.
                if (conductor.resourceStatus().state() == ResourceState.AVAILABLE) {
                    walk(conductor);
                }
            }
            case WALKING -> {
                if (ticksInStep == 40) {
                    OperationResult result = conductor.cancel(operation);
                    // CANCELLING means "cancel requested". The bot may keep moving until the engine lets go.
                    LOG.info("cancel requested: {}", result.status().map(s -> s.state()).orElse(null));
                    next(Step.STOPPING);
                }
            }
            case STOPPING -> {
                OperationState state = conductor.operationStatus(operation).status().orElseThrow().state();
                if (state == OperationState.CANCELLED) {
                    LOG.info("stop confirmed after {} ticks; resource is {}", ticksInStep, conductor.resourceStatus().state());
                    next(Step.DONE);
                } else if (state == OperationState.UNCERTAIN) {
                    LOG.warn("stop could not be confirmed: {}", conductor.operationStatus(operation).status().orElseThrow().detail());
                    next(Step.DONE);
                }
            }
            case DONE -> {
            }
        }
    }

    private void walk(Conductor conductor) {
        // Observations come with a freshness rule you choose; there is no default.
        ObservationResult<Boolean> segment = conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick());
        LOG.info("engine path segment before walking: {}", segment.observation().flatMap(o -> o.observedValue()).orElse(null));

        BlockPos here = Minecraft.getInstance().player.blockPosition();
        OperationResult result = conductor.requestTravel(NavigationGoal.getToBlock(here.getX() + 20, here.getY(), here.getZ()));
        if (!result.isAccepted()) {
            // Every refusal says why, e.g. RESOURCE_NOT_RELEASED while another process is steering.
            LOG.info("travel refused: {} ({})", result.rejection().orElseThrow(), result.detail());
            return; // try again next tick
        }
        operation = result.status().orElseThrow().id();
        LOG.info("walking: operation {} is {}", operation.id(), result.status().orElseThrow().state());
        next(Step.WALKING);
    }

    private void next(Step nextStep) {
        step = nextStep;
        ticksInStep = 0;
    }
}
