package conductor.example.actions;

import conductor.contract.ActionResult;
import conductor.contract.BlockPoint;
import conductor.contract.ConductorModule;
import conductor.contract.EntityInfo;
import conductor.contract.FreshnessPolicy;
import conductor.contract.Lease;
import conductor.contract.LeaseResult;
import conductor.contract.ModuleContext;
import conductor.contract.ObservationKind;
import conductor.contract.ObservationKinds;
import conductor.contract.PlayerMotion;
import conductor.contract.ReleasePolicy;
import conductor.contract.ResourceKey;
import conductor.contract.ResourceState;
import conductor.contract.Resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * A tour of Conductor's player, world and action capabilities, in the order a client-mod module would use them:
 * read the player, the world and nearby entities; take the leases; switch hotbar slot, say something, turn toward
 * the nearest hostile mob and hit it; then give everything back. Every action is checked against the next
 * observation, because "accepted" only means the game was told.
 * <p>
 * Imports only {@code conductor.contract}: the same jar runs on every supported version.
 */
public final class PlayerActions implements ConductorModule {

    private static final System.Logger LOG = System.getLogger("player-actions");
    private static final List<ResourceKey> NEEDED = List.of(Resources.HOTBAR, Resources.LOOK, Resources.INTERACT, Resources.CHAT);

    private enum Step { SETTLE, READ, LEASE, HOTBAR, HOTBAR_CHECK, CHAT, FIND_MOB, LOOK_CHECK, HIT_CHECK, RELEASE, DONE }

    private Step step = Step.SETTLE;
    private int ticksInStep;
    private final List<Lease> leases = new ArrayList<>();
    private int wantedSlot;
    private EntityInfo mob;
    private double wantedYaw;

    @Override
    public ReleasePolicy releasePolicy() {
        return new ReleasePolicy(OptionalLong.of(20), OptionalLong.of(1200));
    }

    @Override
    public void onReady(ModuleContext conductor) {
        log("ready; capabilities: %s", conductor.discover().capabilities().stream()
                .map(c -> c.id() + c.operations()).toList());
    }

    @Override
    public void onTick(ModuleContext c) {
        ticksInStep++;
        switch (step) {
            case SETTLE -> {
                if (ticksInStep >= 60 && c.resourceStatus().state() == ResourceState.AVAILABLE) {
                    next(Step.READ);
                }
            }
            case READ -> {
                log("status: %s", read(c, ObservationKinds.PLAYER_STATUS).orElse(null));
                log("motion: %s", read(c, ObservationKinds.PLAYER_MOTION).orElse(null));
                log("effects: %s", read(c, ObservationKinds.ACTIVE_EFFECTS).orElse(null));
                log("slots: %s", read(c, ObservationKinds.INVENTORY_SLOTS).orElse(null));
                log("selected slot: %s", read(c, ObservationKinds.SELECTED_SLOT).orElse(null));
                log("entities: %s", read(c, ObservationKinds.NEARBY_ENTITIES).map(l -> l.stream().map(e -> e.type() + "@" + Math.round(e.distance())).toList()).orElse(null));
                Optional<BlockPoint> here = read(c, ObservationKinds.PLAYER_POSITION);
                here.ifPresent(h -> {
                    log("block below: %s", c.blockAt(h.offset(0, -1, 0)).block().orElse("(not loaded)"));
                    log("block 10000 east: %s", c.blockAt(h.offset(10000, 0, 0)).block().orElse("(not loaded)"));
                });
                var search = c.findBlocks(Set.of("minecraft:bedrock"), 8, 3);
                log("nearest bedrock: %s %s", search.found(), search.rejection() == null ? "" : search.rejection());
                next(Step.LEASE);
            }
            case LEASE -> {
                for (ResourceKey r : NEEDED) {
                    LeaseResult lr = c.acquire(r);
                    log("lease %s: %s", r.name(), lr.status());
                    lr.lease().ifPresent(leases::add);
                }
                ActionResult early = c.look(0, 0);
                log("look while holding LOOK: %s", early.isAccepted() ? "accepted" : early.rejection());
                next(Step.HOTBAR);
            }
            case HOTBAR -> {
                int current = read(c, ObservationKinds.SELECTED_SLOT).orElse(0);
                wantedSlot = (current + 3) % 9;
                log("selectHotbar(%d): %s", wantedSlot, describe(c.selectHotbar(wantedSlot)));
                next(Step.HOTBAR_CHECK);
            }
            case HOTBAR_CHECK -> {
                int now = read(c, ObservationKinds.SELECTED_SLOT).orElse(-1);
                log("selected slot is now %d: %s", now, now == wantedSlot ? "CONFIRMED" : "NOT CONFIRMED");
                next(Step.CHAT);
            }
            case CHAT -> {
                log("sendChat: %s", describe(c.sendChat("Conductor player-actions module: hello")));
                log("second sendChat same tick: %s", describe(c.sendChat("again")));
                next(Step.FIND_MOB);
            }
            case FIND_MOB -> {
                Optional<EntityInfo> target = read(c, ObservationKinds.NEARBY_ENTITIES)
                        .flatMap(l -> l.stream().filter(EntityInfo::hostile).filter(e -> e.distance() < 3.2).findFirst());
                Optional<PlayerMotion> me = read(c, ObservationKinds.PLAYER_MOTION);
                if (target.isPresent() && me.isPresent()) {
                    mob = target.get();
                    double dx = mob.x() - me.get().x(), dz = mob.z() - me.get().z();
                    double dy = (mob.y() + 1.0) - (me.get().y() + 1.62);
                    wantedYaw = Math.toDegrees(Math.atan2(-dx, dz));
                    double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                    log("hostile %s (id %d) at %.1f blocks, health %s; look(%.1f, %.1f): %s", mob.type(), mob.id(), mob.distance(),
                            mob.health(), wantedYaw, pitch, describe(c.look(wantedYaw, pitch)));
                    next(Step.LOOK_CHECK);
                } else if (ticksInStep > 1200) {
                    log("no hostile mob within reach after 60 s");
                    next(Step.RELEASE);
                }
            }
            case LOOK_CHECK -> {
                double yaw = read(c, ObservationKinds.PLAYER_MOTION).map(PlayerMotion::yaw).orElse(Double.NaN);
                double diff = Math.abs(((yaw - wantedYaw) % 360 + 540) % 360 - 180);
                log("yaw is now %.1f: %s", yaw, diff < 1 ? "CONFIRMED" : "NOT CONFIRMED");
                log("attack(%d): %s", mob.id(), describe(c.attack(mob.id())));
                next(Step.HIT_CHECK);
            }
            case HIT_CHECK -> {
                if (ticksInStep == 10) {
                    Optional<EntityInfo> after = read(c, ObservationKinds.NEARBY_ENTITIES)
                            .flatMap(l -> l.stream().filter(e -> e.id() == mob.id()).findFirst());
                    log("mob health before %s, after %s: %s", mob.health(), after.map(EntityInfo::health).orElse(null),
                            after.isEmpty() || after.get().health().orElse(0) < mob.health().orElse(0) ? "HIT CONFIRMED" : "NO DAMAGE SEEN");
                    log("attack(-1): %s", describe(c.attack(-1)));
                    log("useItem(offhand): %s", describe(c.useItem(true)));
                    next(Step.RELEASE);
                }
            }
            case RELEASE -> {
                leases.forEach(c::release);
                leases.clear();
                log("leases released; look without LOOK: %s", describe(c.look(0, 0)));
                next(Step.DONE);
            }
            case DONE -> {
            }
        }
    }

    private static <T> Optional<T> read(ModuleContext c, ObservationKind<T> kind) {
        return c.observe(kind, FreshnessPolicy.sameTick()).observation().flatMap(o -> o.observedValue());
    }

    private static String describe(ActionResult r) {
        return r.isAccepted() ? "accepted" : r.rejection() + " (" + r.detail() + ")";
    }

    private void next(Step s) {
        step = s;
        ticksInStep = 0;
    }

    private static void log(String format, Object... args) {
        LOG.log(System.Logger.Level.INFO, String.format(format, args));
    }
}
