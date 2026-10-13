package conductor.example.engine;

import conductor.contract.ActionResult;
import conductor.contract.ConductorModule;
import conductor.contract.EngineCommands;
import conductor.contract.Lease;
import conductor.contract.LeaseResult;
import conductor.contract.ModuleContext;
import conductor.contract.Priority;
import conductor.contract.ReleasePolicy;
import conductor.contract.ResourceState;
import conductor.contract.Resources;
import conductor.contract.Setting;
import conductor.contract.Topic;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Uses the engine's own commands through {@link EngineCommands} (Ostinato's {@code #goto}, {@code #mine}, ...) while
 * sharing the bot with the player's Ostinato commands. Contract-only: one jar for every version.
 * <p>
 * Type {@code +enginetest} (or set {@code selftest=true}) to run the tour:
 * <ol>
 *   <li>"the player" types {@code #goto ~40 ~ ~}; Ostinato's module takes MOVEMENT for it;</li>
 *   <li>this module (SAFETY priority) takes MOVEMENT over: Ostinato is stopped, and the lease arrives only once the
 *       stop is proven;</li>
 *   <li>it runs {@code goto ~-20 ~ ~} through {@link EngineCommands} under its own lease;</li>
 *   <li>"the player" types {@code #goto ~5 ~ ~}: refused, because this module holds MOVEMENT;</li>
 *   <li>it stops the engine, waits for proof, and gives MOVEMENT back; running a task without the lease is refused.</li>
 * </ol>
 */
public final class EngineCommandsDemo implements ConductorModule {

    static final Setting<Boolean> SELFTEST = Setting.bool("selftest", "run the tour on its own", false);
    static final Topic<String> OSTINATO_TASKS = new Topic<>("ostinato:tasks", String.class);
    private static final System.Logger LOG = System.getLogger("engine-commands");

    private int step = -1;
    private int ticks;
    private Lease movement;
    private boolean ostinatoRefusedToRun;

    @Override
    public ReleasePolicy releasePolicy() {
        return new ReleasePolicy(OptionalLong.of(20), OptionalLong.of(1200));
    }

    @Override
    public Priority priority() {
        return Priority.SAFETY;
    }

    /** Started only once an engine module provides its commands (Conductor orders the start). */
    @Override
    public java.util.Set<conductor.contract.ServiceKey<?>> requires() {
        return java.util.Set.of(EngineCommands.KEY);
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(SELFTEST);
    }

    @Override
    public void onReady(ModuleContext c) {
        c.command("enginetest", "run the engine-commands tour", call -> start());
        c.subscribe(OSTINATO_TASKS, s -> log("ostinato:tasks -> %s", s));
        log("ready; engine commands service: %s", c.service(EngineCommands.KEY).map(EngineCommands::engine).orElse("NONE"));
        if (c.get(SELFTEST)) {
            start();
        }
    }

    private void start() {
        step = 0;
        ticks = 0;
    }

    @Override
    public void onLeaseGranted(ModuleContext c, Lease lease) {
        if (lease.resource().equals(Resources.MOVEMENT)) {
            movement = lease;
            log("MOVEMENT granted after %d ticks: Ostinato's stop was proven", ticks);
        }
    }

    @Override
    public void onTick(ModuleContext c) {
        if (step < 0) {
            return;
        }
        ticks++;
        Optional<EngineCommands> engine = c.service(EngineCommands.KEY);
        switch (step) {
            case 0 -> {
                if (ticks >= 60 && c.resourceStatus().state() == ResourceState.AVAILABLE) {
                    c.acquire(Resources.CHAT);
                    log("player types #goto ~40 ~ ~: %s", describe(c.sendChat("#goto ~40 ~ ~")));
                    next();
                }
            }
            case 1 -> {
                if (ticks == 60) {
                    log("after 3 s: bot %s; MOVEMENT held by %s", c.resourceStatus().state(), holder(c));
                    LeaseResult r = c.acquire(Resources.MOVEMENT); // SAFETY
                    log("SAFETY module asks for MOVEMENT: %s (%s)", r.status(), r.detail());
                    r.lease().filter(l -> r.isGranted()).ifPresent(l -> movement = l);
                    next();
                }
            }
            case 2 -> {
                if (movement != null) {
                    log("engine.run(goto ~-20 ~ ~): %s", engine.map(e -> describe(e.run(c, "goto ~-20 ~ ~"))).orElse("no service"));
                    next();
                } else if (ticks > 1200) {
                    log("MOVEMENT never arrived");
                    step = -1;
                }
            }
            case 3 -> {
                if (ticks == 20) {
                    log("20 ticks after engine.run: bot %s (busy means Ostinato is moving it for this module)", c.resourceStatus().state());
                } else if (ticks == 40) {
                    log("player types #goto ~5 ~ ~ while this module holds MOVEMENT: %s", describe(c.sendChat("#goto ~5 ~ ~")));
                } else if (ticks == 80) {
                    log("MOVEMENT still held by %s: %s", holder(c), holder(c).equals(c.self().value()) ? "player command REFUSED as expected" : "UNEXPECTED");
                    log("engine.stop: %s", engine.map(e -> describe(e.stop(c))).orElse("no service"));
                    next();
                }
            }
            case 4 -> {
                if (c.resourceStatus().state() == ResourceState.AVAILABLE) {
                    log("stop proven after %d ticks; releasing MOVEMENT", ticks);
                    c.release(movement);
                    movement = null;
                    log("engine.run(mine diamond_ore) without MOVEMENT: %s",
                            engine.map(e -> describe(e.run(c, "mine diamond_ore"))).orElse("no service"));
                    log("engine.run(version) (not a task, no lease needed): %s",
                            engine.map(e -> describe(e.run(c, "version"))).orElse("no service"));
                    log("tour done");
                    step = -1;
                } else if (ticks > 1200) {
                    log("stop NOT proven within 60 s");
                    step = -1;
                }
            }
            default -> {
            }
        }
    }

    private static String holder(ModuleContext c) {
        return c.leases().stream().filter(l -> l.resource().equals(Resources.MOVEMENT))
                .map(l -> l.holder().value()).findFirst().orElse("nobody");
    }

    private void next() {
        step++;
        ticks = 0;
    }

    private static String describe(ActionResult r) {
        return r.isAccepted() ? "accepted" : r.rejection() + " (" + r.detail() + ")";
    }

    private static void log(String format, Object... args) {
        LOG.log(System.Logger.Level.INFO, String.format(format, args));
    }
}
