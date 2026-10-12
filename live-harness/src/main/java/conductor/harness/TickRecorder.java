package conductor.harness;

import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import conductor.contract.Conductor;
import conductor.contract.FreshnessPolicy;
import conductor.contract.ObservationKind;
import conductor.contract.ObservationKinds;
import conductor.contract.ObservationResult;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records one line per in-world client tick: every observation kind through the live contract, plus the player's
 * position and velocity read straight from Minecraft as physical ground truth. Runs on the game thread.
 */
final class TickRecorder implements AbstractGameEventListener {

    private final Conductor conductor;
    private final JsonLog log;
    volatile String phase = "setup";
    volatile long ticks;
    // latest values, read by the scenario thread to decide when the engine looks idle
    volatile boolean segment, next, calc, goalActive, inputsForced, processInControl;
    volatile double horizontalSpeed;
    volatile conductor.contract.OperationId operation;
    volatile String resourceState = "", operationState = "";

    TickRecorder(Conductor conductor, JsonLog log) {
        this.conductor = conductor;
        this.log = log;
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = mc.player.position();
        Vec3 vel = mc.player.getDeltaMovement();
        double hs = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        Map<String, Object> obs = new LinkedHashMap<>();
        for (ObservationKind<?> kind : ObservationKinds.ALL) {
            ObservationResult<?> r = conductor.observe(kind, FreshnessPolicy.sameTick());
            obs.put(kind.name(), r.isAccepted()
                    ? r.observation().get().observedValue().map(v -> (Object) String.valueOf(v)).orElse(r.observation().get().availability().name())
                    : "REJECTED " + r.rejection().get());
        }
        segment = "true".equals(obs.get("PATH_SEGMENT_PRESENT"));
        next = "true".equals(obs.get("NEXT_SEGMENT_PRESENT"));
        calc = "true".equals(obs.get("PATH_CALCULATION_PENDING"));
        goalActive = "true".equals(obs.get("TRAVEL_GOAL_ACTIVE"));
        inputsForced = !"[]".equals(obs.get("FORCED_INPUTS"));
        processInControl = !"Optional.empty".equals(obs.get("CONTROLLING_PROCESS"));
        horizontalSpeed = hs;
        resourceState = String.valueOf(conductor.resourceStatus().state());
        conductor.contract.OperationId op = operation;
        operationState = op == null ? "" : conductor.operationStatus(op).status().map(s -> s.state().name()).orElse("?");
        log.write(JsonLog.obj("type", "tick", "t", event.getCount(), "phase", phase,
                "resource", resourceState, "op", operationState,
                "dim", mc.level.dimension().identifier().toString(),
                "x", round(pos.x), "y", round(pos.y), "z", round(pos.z),
                "vx", round(vel.x), "vz", round(vel.z), "hspeed", round(hs), "obs", obs));
        ticks++;
    }

    boolean looksIdle() {
        return !segment && !next && !calc && !goalActive && !inputsForced && !processInControl && horizontalSpeed < 0.003;
    }

    private static double round(double d) {
        return Math.round(d * 10000.0) / 10000.0;
    }
}
