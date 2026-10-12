package conductor.harness;

import baritone.api.IBaritone;
import baritone.api.movement.IMovementEngine;
import baritone.api.movement.MovementGoal;
import baritone.api.movement.PathResult;
import baritone.movement.MovementBackends;
import net.minecraft.core.BlockPos;

/**
 * Stage 4 evidence trials, calling the existing engine directly (outside the contract): cancel timing on varied
 * terrain, a long multi-segment path, a delayed-result probe, and a concurrent mining process. Courses are built
 * with commands on a flat world (ground y=-61, bedrock y=-64 left intact). Needs op.
 */
final class EvidenceTrials {

    private static final int Y = -60;

    private final Scenario s;
    private final IBaritone baritone;
    private IMovementEngine engine;

    EvidenceTrials(Scenario scenario, IBaritone baritone) {
        this.s = scenario;
        this.baritone = baritone;
    }

    void run() throws Exception {
        engine = s.onGame(() -> MovementBackends.engine(baritone));
        buildCourses();

        // varied terrain, two cancel timings each
        for (int k : new int[]{15, 40}) {
            trial("steps", new BlockPos(0, Y, 20), new BlockPos(30, Y, 20), k, 250);
            trial("trench", new BlockPos(0, Y, 40), new BlockPos(30, Y, 40), k, 250);
            trial("water", new BlockPos(0, Y, 60), new BlockPos(35, Y, 60), k, 250);
        }
        // long path: beyond loaded chunks, so the engine plans several segments
        trial("long", new BlockPos(0, Y, -30), new BlockPos(160, Y, -30), 30, 700);
        trial("long", new BlockPos(0, Y, -30), new BlockPos(160, Y, -30), 150, 700);
        // delayed-result probe: cancel around the time the first calculation finishes
        for (int rep = 0; rep < 3; rep++) {
            for (int k = 1; k <= 4; k++) {
                trial("race", new BlockPos(0, Y, 0), new BlockPos(40, Y, 0), k, 150);
            }
        }
        mining();
    }

    private void buildCourses() throws Exception {
        s.recorder.phase = "build";
        cmd("tp @s 0 -60 0");
        s.waitTicks(20);
        // staircase up to 6 high and back down, 5 wide (z 18..22)
        for (int i = 0; i < 6; i++) {
            cmd("fill " + (8 + i) + " -60 18 " + (8 + i) + " " + (-60 + i) + " 22 minecraft:stone");
            cmd("fill " + (14 + i) + " -60 18 " + (14 + i) + " " + (-60 + 5 - i) + " 22 minecraft:stone");
        }
        // trench 2 wide, 3 deep, z 30..50
        cmd("fill 12 -63 30 13 -61 50 minecraft:air");
        // water pool 3 deep
        cmd("fill 10 -63 55 25 -61 65 minecraft:water");
        // logs for the mining trial
        cmd("fill 20 -60 -62 22 -58 -58 minecraft:oak_log");
        s.waitTicks(40);
        s.event("courses built");
    }

    private void trial(String course, BlockPos start, BlockPos target, int k, int window) throws Exception {
        s.recorder.phase = "E " + course + " k=" + k + " reset";
        cmd("tp @s " + start.getX() + ".5 " + start.getY() + " " + start.getZ() + ".5");
        s.waitTicks(20);
        boolean idle = s.waitIdle(10, 300);
        s.recorder.phase = "E " + course + " k=" + k + " moving";
        PathResult result = s.onGame(() -> engine.goTo(MovementGoal.getToBlock(target)));
        s.event("E goTo", "course", course, "k", k, "idleBefore", idle, "from", String.valueOf(start),
                "target", String.valueOf(target), "result", String.valueOf(result));
        s.waitTicks(k);
        s.onGame(() -> {
            engine.cancel();
            return null;
        });
        s.event("E cancel", "course", course, "k", k, "window", window,
                "tx", target.getX(), "ty", target.getY(), "tz", target.getZ());
        s.recorder.phase = "E " + course + " k=" + k + " after cancel";
        s.waitTicks(window);
    }

    private void mining() throws Exception {
        s.recorder.phase = "M reset";
        cmd("tp @s 10.5 -60 -60.5");
        s.waitTicks(20);
        s.waitIdle(10, 300);
        s.recorder.phase = "M mining";
        s.onGame(() -> {
            baritone.getMineProcess().mineByName(0, "oak_log");
            return null;
        });
        s.event("M mine started");
        s.waitTicks(80);
        s.event("M before cancel", "mineActive", s.onGame(() -> baritone.getMineProcess().isActive()));
        s.onGame(() -> {
            engine.cancel();
            return null;
        });
        s.event("E cancel", "course", "mining", "k", 80, "window", 200, "tx", 21, "ty", -59, "tz", -60);
        s.recorder.phase = "M after cancel";
        s.waitTicks(200);
        s.event("M after window", "mineActive", s.onGame(() -> baritone.getMineProcess().isActive()));
    }

    private void cmd(String command) throws Exception {
        s.onGame(() -> {
            s.mc.player.connection.sendCommand(command);
            return null;
        });
        s.waitTicks(2);
    }
}
