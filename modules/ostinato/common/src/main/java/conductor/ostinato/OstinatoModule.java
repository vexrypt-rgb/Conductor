package conductor.ostinato;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.ICommand;
import baritone.api.command.manager.ICommandManager;
import baritone.movement.MovementBackends;
import conductor.contract.ActionResult;
import conductor.contract.ConductorModule;
import conductor.contract.EngineCommands;
import conductor.contract.Lease;
import conductor.contract.LeaseResult;
import conductor.contract.ModuleContext;
import conductor.contract.ModuleId;
import conductor.contract.Priority;
import conductor.contract.RejectionReason;
import conductor.contract.ReleasePolicy;
import conductor.contract.ResourceState;
import conductor.contract.Resources;
import conductor.contract.Setting;
import conductor.contract.Topic;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Ostinato as a Conductor module.
 * <ul>
 *   <li><b>Ostinato's own commands share the controls.</b> Before a command that moves the bot runs (from chat, the
 *       GUI or code), this module takes Conductor's MOVEMENT lease for it, through Ostinato's command execution
 *       guard. If a module with an equal or higher priority holds MOVEMENT, the command is refused with the reason;
 *       if a lower one does, Conductor stops that module first (proven by evidence) and the command runs then.</li>
 *   <li><b>A higher-priority module can take over</b> a running Ostinato task (a fight, an escape): Ostinato is
 *       stopped, and the hand-over waits for proof that the bot let go.</li>
 *   <li><b>Other modules can use every Ostinato feature</b> through {@link EngineCommands} (mine, follow, explore,
 *       farm, build, ...), under their own MOVEMENT lease.</li>
 * </ul>
 */
public final class OstinatoModule implements ConductorModule {

    /** Ostinato commands that move the bot (any of their names). */
    static final Set<String> TASK_COMMANDS = Set.of("goto", "path", "build", "schematica", "litematica", "come",
            "axis", "highway", "invert", "tunnel", "farm", "follow", "pvp", "pve", "bastion", "pickup", "explore",
            "mine", "click", "surface", "top", "waypoints", "waypoint", "wp", "sel", "selection", "s", "elytra",
            "tasks", "task", "portal", "resume");

    /** Every engine process Ostinato registers; while this module (or a caller) holds MOVEMENT they are its own. */
    static final Set<String> PROCESSES = Set.of("baritone.process.AirProcess", "baritone.process.BackfillProcess",
            "baritone.process.BastionProcess", "baritone.process.BoatProcess", "baritone.process.BuilderProcess",
            "baritone.process.CustomGoalProcess", "baritone.process.ElytraProcess", "baritone.process.ExploreProcess",
            "baritone.process.FarmProcess", "baritone.process.FollowProcess", "baritone.process.GetToBlockProcess",
            "baritone.process.InventoryPauserProcess", "baritone.process.MineProcess",
            "baritone.process.PortalBuildProcess", "baritone.process.PveProcess", "baritone.process.PvpProcess",
            "baritone.process.elytra.NullElytraProcess");

    static final Setting<Boolean> PLAYER_FIRST = Setting.bool("player-first",
            "your own # commands outrank NORMAL modules (HIGH); off: they wait like any NORMAL module", true);
    static final Setting<Boolean> QUEUE = Setting.bool("queue",
            "when a lower-priority module must stop first, run the command once it has; off: refuse it", true);

    /** What Ostinato is doing for the player: "running <command>", "queued <command>", "stopped: ...", "done". */
    public static final Topic<String> TASKS = new Topic<>("ostinato:tasks", String.class);

    private static final System.Logger LOG = System.getLogger("conductor-ostinato");

    private ModuleContext ctx;
    private IBaritone baritone;
    private Lease held;
    private String running;
    private String queued;
    private boolean sawBusy;
    private int idleTicks;
    private boolean bypass;

    @Override
    public ReleasePolicy releasePolicy() {
        return new ReleasePolicy(OptionalLong.of(20), OptionalLong.of(1200));
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(PLAYER_FIRST, QUEUE);
    }

    @Override
    public void onReady(ModuleContext c) {
        ctx = c;
        baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getCommandManager().setExecutionGuard(this::guard);
        c.provide(EngineCommands.KEY, new Commands());
        c.command("ostinato", "what Ostinato is doing and who holds movement", call -> c.showMessage(status()));
        log("ready: Ostinato's movement commands now take Conductor's MOVEMENT lease");
    }

    @Override
    public void onDisable(ModuleContext c) {
        if (baritone != null) {
            baritone.getCommandManager().setExecutionGuard(null); // Ostinato goes back to running everything
        }
    }

    // ---- the guard: Ostinato asks before any command runs ----

    private Optional<String> guard(ICommand command, String line) {
        if (bypass || !isTask(command)) {
            return Optional.empty();
        }
        if (held != null) {
            started(line); // a new task replaces Ostinato's current one under the same lease
            return Optional.empty();
        }
        Priority priority = ctx.get(PLAYER_FIRST) ? Priority.HIGH : Priority.NORMAL;
        LeaseResult r = ctx.acquire(Resources.MOVEMENT, priority);
        switch (r.status()) {
            case GRANTED -> {
                held = r.lease().orElseThrow();
                started(line);
                return Optional.empty();
            }
            case PENDING -> {
                if (ctx.get(QUEUE)) {
                    queued = line;
                    ctx.publish(TASKS, "queued " + line);
                    return Optional.of("movement is still in use" + r.holder().map(h -> " by " + h.value()).orElse("")
                            + "; #" + line + " will run as soon as it is free");
                }
                r.lease().ifPresent(ctx::release); // withdraw the request
                return Optional.of("movement is still in use" + r.holder().map(h -> " by " + h.value()).orElse(""));
            }
            default -> {
                return Optional.of("movement is " + r.detail() + "; try again later, or +disable "
                        + r.holder().map(ModuleId::value).orElse("that module"));
            }
        }
    }

    private static boolean isTask(ICommand command) {
        return command != null && command.getNames().stream().anyMatch(n -> TASK_COMMANDS.contains(n.toLowerCase(Locale.ROOT)));
    }

    private void started(String line) {
        ctx.declareEngineProcesses(PROCESSES);
        running = line;
        sawBusy = false;
        idleTicks = 0;
        ctx.publish(TASKS, "running " + line);
        log("running #%s under the MOVEMENT lease", line);
    }

    @Override
    public void onLeaseGranted(ModuleContext c, Lease lease) {
        if (!lease.resource().equals(Resources.MOVEMENT) || queued == null) {
            return;
        }
        held = lease;
        String line = queued;
        queued = null;
        started(line);
        c.showMessage("running queued #" + line);
        bypass = true;
        try {
            baritone.getCommandManager().execute(line);
        } finally {
            bypass = false;
        }
    }

    @Override
    public void onLeaseRevoked(ModuleContext c, Lease lease, ModuleId by) {
        if (!lease.resource().equals(Resources.MOVEMENT)) {
            return;
        }
        MovementBackends.engine(baritone).cancel(); // Conductor hands MOVEMENT over only once the bot is seen to stop
        String what = running == null ? "Ostinato's task" : "#" + running;
        c.showMessage(what + " stopped: " + by.value() + " took over");
        c.publish(TASKS, "stopped: " + by.value() + " took over");
        log("%s stopped: %s took over", what, by.value());
        held = null;
        running = null;
    }

    @Override
    public void onTick(ModuleContext c) {
        if (held == null) {
            return;
        }
        // Give the lease back once Ostinato is done: the bot was busy and is now proven idle, or the command never
        // started anything.
        if (c.resourceStatus().state() == ResourceState.AVAILABLE) {
            idleTicks++;
            if (sawBusy || idleTicks > 100) {
                c.release(held);
                c.publish(TASKS, "done " + running);
                log("#%s done; MOVEMENT released", running);
                held = null;
                running = null;
            }
        } else {
            sawBusy = true;
            idleTicks = 0;
        }
    }

    private String status() {
        String holder = ctx.leases().stream().filter(l -> l.resource().equals(Resources.MOVEMENT))
                .map(l -> l.holder().value() + " at " + l.priority()).findFirst().orElse("nobody");
        return "Ostinato " + (running != null ? "is running #" + running : "has no task")
                + (queued != null ? ", queued #" + queued : "") + "; MOVEMENT held by " + holder
                + "; bot " + ctx.resourceStatus().state();
    }

    private static void log(String format, Object... args) {
        LOG.log(System.Logger.Level.INFO, String.format(format, args));
    }

    // ---- the service other modules use ----

    private final class Commands implements EngineCommands {

        @Override
        public String engine() {
            return "Ostinato";
        }

        @Override
        public Set<String> taskCommands() {
            return TASK_COMMANDS;
        }

        @Override
        public ActionResult run(ModuleContext caller, String commandLine) {
            if (commandLine == null || commandLine.isBlank()) {
                return ActionResult.rejected(RejectionReason.INVALID_ARGUMENT, "empty command");
            }
            ICommandManager manager = baritone.getCommandManager();
            String label = commandLine.trim().split("\\s+", 2)[0];
            ICommand command = manager.getCommand(label);
            if (command == null) {
                return ActionResult.rejected(RejectionReason.INVALID_ARGUMENT, "Ostinato has no command " + label);
            }
            if (isTask(command)) {
                if (!holdsMovement(caller)) {
                    return ActionResult.rejected(RejectionReason.LEASE_REQUIRED, label + " moves the bot; hold MOVEMENT first");
                }
                caller.declareEngineProcesses(PROCESSES);
            }
            bypass = true;
            try {
                manager.execute(commandLine.trim());
            } finally {
                bypass = false;
            }
            return ActionResult.done("ran " + label);
        }

        @Override
        public ActionResult stop(ModuleContext caller) {
            if (!holdsMovement(caller)) {
                return ActionResult.rejected(RejectionReason.LEASE_REQUIRED, "hold MOVEMENT to stop the engine");
            }
            MovementBackends.engine(baritone).cancel();
            return ActionResult.done("cancel requested; proven when resourceStatus() is AVAILABLE");
        }

        private boolean holdsMovement(ModuleContext caller) {
            return caller != null && caller.leases().stream()
                    .anyMatch(l -> l.resource().equals(Resources.MOVEMENT) && l.holder().equals(caller.self()));
        }
    }
}
