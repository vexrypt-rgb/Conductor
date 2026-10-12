package conductor.example.combat;

import conductor.contract.CombatGoal;
import conductor.contract.ConductorModule;
import conductor.contract.ContentId;
import conductor.contract.FeatureStatus;
import conductor.contract.FreshnessPolicy;
import conductor.contract.Lease;
import conductor.contract.ModuleContext;
import conductor.contract.ModuleFeature;
import conductor.contract.ModuleId;
import conductor.contract.ObservationKinds;
import conductor.contract.OperationId;
import conductor.contract.OperationResult;
import conductor.contract.OperationState;
import conductor.contract.Priority;
import conductor.contract.ReleasePolicy;
import conductor.contract.ResourceState;
import conductor.contract.Topic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A combat module written once for every Minecraft version.
 * <p>
 * It asks what exists instead of checking a version. Spears exist on 1.21.11 but not on 1.21.4, so on 1.21.4 the
 * spear feature is off: spears are not part of the kit, never checked for, never reported. Everything else runs
 * normally. The same goes for maces and wind charges, which also need the engine build to have the code for them.
 * <p>
 * The fighting itself is Ostinato's (through Conductor's combat capability), which already picks weapons from what
 * the player carries. This module decides <em>when</em> to fight, checks the kit for this game, publishes what it is
 * doing for other modules, and stops with proof.
 * <p>
 * Settings (JVM system properties): {@code agnostic.combat.goal} = {@code hostiles} (default), {@code players},
 * {@code player:<name>} or {@code mob:<entity id>}; {@code agnostic.combat.idleStop} = ticks without a target, after a
 * fight, before stopping (default 100); {@code agnostic.combat.giveUp} = ticks without ever seeing a target before
 * stopping (default 1200).
 */
public final class AgnosticCombat implements ConductorModule {

    /** What this module is doing, for HUD, logging or coordinating modules: "engaged hostiles", "target ...", ... */
    public static final Topic<String> FIGHT = new Topic<>("agnostic-combat:fight", String.class);

    private static final System.Logger LOG = System.getLogger("agnostic-combat");

    /** One part of the kit. {@code feature} null means every version has it. */
    private record KitPart(String role, String feature, List<String> anyOf) {}

    private static final List<String> SPEARS = List.of("minecraft:wooden_spear", "minecraft:stone_spear",
            "minecraft:copper_spear", "minecraft:iron_spear", "minecraft:golden_spear", "minecraft:diamond_spear",
            "minecraft:netherite_spear");

    private static final List<KitPart> KIT = List.of(
            new KitPart("sword", null, List.of("minecraft:netherite_sword", "minecraft:diamond_sword",
                    "minecraft:iron_sword", "minecraft:stone_sword", "minecraft:golden_sword", "minecraft:wooden_sword")),
            new KitPart("shield", null, List.of("minecraft:shield")),
            new KitPart("spear", "spear", SPEARS),
            new KitPart("mace", "mace", List.of("minecraft:mace")),
            new KitPart("wind charges", "wind", List.of("minecraft:wind_charge")));

    private enum Step { WAIT_FOR_RESOURCE, FIGHTING, STOPPING, DONE }

    private final CombatGoal goal = parseGoal(System.getProperty("agnostic.combat.goal", "hostiles"));
    private final int idleStop = Integer.getInteger("agnostic.combat.idleStop", 100);
    private final int giveUp = Integer.getInteger("agnostic.combat.giveUp", 1200);

    private List<KitPart> kit = List.of();
    private String lastKitReport = "";
    private Step step = Step.WAIT_FOR_RESOURCE;
    private OperationId operation;
    private int ticksInStep;
    private int ticksWithoutTarget;
    private boolean sawTarget;
    private Optional<String> lastTarget = Optional.empty();

    @Override
    public ReleasePolicy releasePolicy() {
        return new ReleasePolicy(OptionalLong.of(20), OptionalLong.of(1200));
    }

    /** A fight outranks walking around: HIGH lets this module take MOVEMENT from NORMAL modules. */
    @Override
    public Priority priority() {
        return Priority.HIGH;
    }

    @Override
    public List<ModuleFeature> features() {
        return List.of(
                ModuleFeature.of("spear", "carry a spear and let the engine lunge with it",
                        ContentId.item("minecraft:iron_spear"), ContentId.engine("ostinato:combat/spear")),
                ModuleFeature.of("mace", "carry a mace for smash attacks",
                        ContentId.item("minecraft:mace"), ContentId.engine("ostinato:combat/mace")),
                ModuleFeature.of("wind", "carry wind charges for jumps",
                        ContentId.item("minecraft:wind_charge"), ContentId.engine("ostinato:combat/wind")),
                ModuleFeature.of("autotune", "the engine tunes its own combat timing (Autotune Combat System)",
                        ContentId.engine("ostinato:pvp/autotune")));
    }

    @Override
    public void onReady(ModuleContext conductor) {
        for (FeatureStatus f : conductor.features().values()) {
            log("feature %-8s %s", f.name(), f.enabled() ? "ON" : "OFF (" + f.reason() + ")");
        }
        List<KitPart> parts = new ArrayList<>();
        for (KitPart part : KIT) {
            if (part.feature() == null || conductor.feature(part.feature()).enabled()) {
                parts.add(part);
            }
        }
        kit = List.copyOf(parts);
        log("kit for this game: %s", kit.stream().map(KitPart::role).toList());
        log("goal: %s", goal);
    }

    @Override
    public void onTick(ModuleContext conductor) {
        ticksInStep++;
        if (ticksInStep % 20 == 1) {
            reportKit(conductor);
        }
        switch (step) {
            case WAIT_FOR_RESOURCE -> {
                if (conductor.resourceStatus().state() == ResourceState.AVAILABLE) {
                    engage(conductor);
                }
            }
            case FIGHTING -> fight(conductor);
            case STOPPING -> {
                OperationState state = conductor.operationStatus(operation).status().orElseThrow().state();
                if (state == OperationState.CANCELLED || state == OperationState.UNCERTAIN) {
                    log("stop %s after %d ticks", state == OperationState.CANCELLED ? "confirmed" : "NOT confirmed", ticksInStep);
                    conductor.publish(FIGHT, "stopped " + state);
                    next(Step.DONE);
                }
            }
            case DONE -> {
            }
        }
    }

    @Override
    public void onLeaseRevoked(ModuleContext conductor, Lease lease, ModuleId by) {
        log("%s taken by %s; fight handed over", lease.resource().name(), by);
        conductor.publish(FIGHT, "preempted by " + by);
        next(Step.WAIT_FOR_RESOURCE);
    }

    private void engage(ModuleContext conductor) {
        OperationResult result = conductor.requestCombat(goal);
        if (!result.isAccepted()) {
            log("combat refused: %s (%s)", result.rejection().orElseThrow(), result.detail());
            next(Step.DONE);
            return;
        }
        operation = result.status().orElseThrow().id();
        sawTarget = false;
        ticksWithoutTarget = 0;
        log("engaged %s: operation %d", goal, operation.id());
        conductor.publish(FIGHT, "engaged " + goal.kind());
        next(Step.FIGHTING);
    }

    private void fight(ModuleContext conductor) {
        OperationState state = conductor.operationStatus(operation).status().orElseThrow().state();
        if (state != OperationState.EXECUTING) {
            log("fight ended by the engine side: %s", state);
            next(Step.STOPPING);
            return;
        }
        Optional<String> target = conductor.observe(ObservationKinds.COMBAT_TARGET, FreshnessPolicy.sameTick())
                .observation().flatMap(o -> o.observedValue()).flatMap(t -> t);
        if (!target.equals(lastTarget)) {
            log("target: %s", target.orElse("none"));
            conductor.publish(FIGHT, "target " + target.orElse("none"));
            lastTarget = target;
        }
        if (target.isPresent()) {
            sawTarget = true;
            ticksWithoutTarget = 0;
            return;
        }
        ticksWithoutTarget++;
        if ((sawTarget && ticksWithoutTarget >= idleStop) || (!sawTarget && ticksWithoutTarget >= giveUp)) {
            OperationResult r = conductor.cancel(operation);
            log("no target for %d ticks: cancel requested (%s)", ticksWithoutTarget,
                    r.status().map(s -> s.state()).orElse(null));
            next(Step.STOPPING);
        }
    }

    /** Checks only the kit parts this game has. On 1.21.4 there is no spear part to be missing. */
    private void reportKit(ModuleContext conductor) {
        Optional<Map<String, Integer>> inventory = conductor.observe(ObservationKinds.INVENTORY_ITEMS, FreshnessPolicy.sameTick())
                .observation().flatMap(o -> o.observedValue());
        if (inventory.isEmpty()) {
            return;
        }
        List<String> have = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (KitPart part : kit) {
            (part.anyOf().stream().anyMatch(inventory.get()::containsKey) ? have : missing).add(part.role());
        }
        String report = "kit: have " + have + ", missing " + missing;
        if (!report.equals(lastKitReport)) {
            lastKitReport = report;
            log("%s", report);
            conductor.publish(FIGHT, report);
        }
    }

    private void next(Step nextStep) {
        step = nextStep;
        ticksInStep = 0;
    }

    static CombatGoal parseGoal(String s) {
        if (s.startsWith("player:")) {
            return CombatGoal.player(s.substring(7));
        }
        if (s.startsWith("mob:")) {
            return CombatGoal.mob(s.substring(4));
        }
        return s.equals("players") ? CombatGoal.players() : CombatGoal.hostiles();
    }

    private static void log(String format, Object... args) {
        LOG.log(System.Logger.Level.INFO, String.format(format, args));
    }
}
