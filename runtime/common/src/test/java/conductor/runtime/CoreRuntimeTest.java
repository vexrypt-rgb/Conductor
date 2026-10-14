package conductor.runtime;

import conductor.contract.Availability;
import conductor.contract.CapabilityAvailability;
import conductor.contract.CapabilityDescriptor;
import conductor.contract.CombatGoal;
import conductor.contract.Conductor;
import conductor.contract.ContentId;
import conductor.contract.ContentResult;
import conductor.contract.ContextId;
import conductor.contract.DiagnosticEvent;
import conductor.contract.FreshnessPolicy;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.NavigationGoal;
import conductor.contract.Observation;
import conductor.contract.ObservationKinds;
import conductor.contract.ObservationResult;
import conductor.contract.RejectionReason;
import conductor.contract.ReleasePolicy;
import conductor.contract.ResourceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conductor's core runtime with no movement engine installed: lifecycle from its own tick hook, plain game reads,
 * content detection, and release evidence that needs no engine. Nothing is run against a live game.
 */
class CoreRuntimeTest {

    private static final int WINDOW = 3;

    private final FakeGameAccess game = new FakeGameAccess();
    private final InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(1000);
    /** Engine classes this fake build contains. */
    private final Set<String> engineClasses = new java.util.HashSet<>(Jdk8.setOf(
            "baritone.process.PvpProcess", "baritone.process.PveProcess"));

    private ConductorRuntime.Installed installed;
    private Conductor conductor;

    @BeforeEach
    void setUp() {
        installed = TestRuntime.install(sink, new ReleasePolicy(OptionalLong.of(WINDOW), OptionalLong.empty()), game, null,
                engineClasses::contains);
        conductor = installed.service();
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            installed.tick().run();
        }
    }

    private Optional<ContextId> context() {
        return installed.lifecycle().contexts().current();
    }

    private <T> Observation<T> observe(conductor.contract.ObservationKind<T> kind) {
        ObservationResult<T> result = conductor.observe(kind, FreshnessPolicy.sameTick());
        assertTrue(result.isAccepted(), result::toString);
        return result.observation().get();
    }

    // --- context identity from Conductor's own tick hook ---

    @Test
    void pollingTheGameDrivesContextIdentity() {
        assertEquals(Optional.empty(), context());
        ticks(1);
        ContextId first = context().get();

        ticks(1);
        assertEquals(Optional.of(first), context(), "nothing changed, same context");

        game.dimension = "minecraft:the_nether";
        game.level = new Object();
        ticks(1);
        ContextId nether = context().get();
        assertEquals(first.sessionId(), nether.sessionId());
        assertNotEquals(first.epoch(), nether.epoch());
        assertEquals("minecraft:the_nether", nether.dimension());

        game.level = new Object();                       // respawn: new world object, same dimension
        ticks(1);
        ContextId respawned = context().get();
        assertEquals(nether.sessionId(), respawned.sessionId());
        assertNotEquals(nether.epoch(), respawned.epoch());

        game.connection = new Object();
        ticks(1);
        assertNotEquals(respawned.sessionId(), context().get().sessionId());

        game.level = null;
        ticks(1);
        assertEquals(Optional.empty(), context());

        game.level = new Object();
        game.player = null;
        ticks(1);
        assertEquals(Optional.empty(), context(), "a world without a player is not in-world");
    }

    @Test
    void observationFromBeforeADimensionChangeIsRejected() {
        ticks(1);
        FreshnessPolicy policy = FreshnessPolicy.maxAge(OptionalLong.of(100), Optional.empty());
        Observation<?> before = conductor.observe(ObservationKinds.PLAYER_POSITION, policy).observation().get();

        game.dimension = "minecraft:the_end";
        game.level = new Object();
        ticks(1);

        assertEquals(Optional.of(RejectionReason.CONTEXT_INVALIDATED), conductor.revalidate(before, policy).rejection());
    }

    @Test
    void observationsAreRejectedUntilTheFirstInWorldTick() {
        assertEquals(Optional.of(RejectionReason.NO_CURRENT_CONTEXT),
                conductor.observe(ObservationKinds.PLAYER_POSITION, FreshnessPolicy.sameTick()).rejection());
    }

    @Test
    void offThreadCallsAreRejectedAndRecorded() throws InterruptedException {
        ticks(1);
        AtomicReference<ObservationResult<?>> result = new AtomicReference<>();
        Thread other = new Thread(() -> result.set(conductor.observe(ObservationKinds.PLAYER_POSITION, FreshnessPolicy.sameTick())),
                "worker");
        other.start();
        other.join();

        assertEquals(Optional.of(RejectionReason.OFF_THREAD), result.get().rejection());
        assertEquals(1, sink.snapshot().stream().filter(e -> e.type() == DiagnosticEvent.Type.OFF_THREAD_VIOLATION).count());
    }

    // --- plain game reads ---

    @Test
    void playerPositionComesFromGameAccess() {
        ticks(1);
        assertEquals(Optional.of(new conductor.contract.BlockPoint(1, 64, -2)), observe(ObservationKinds.PLAYER_POSITION).observedValue());
    }

    @Test
    void inventoryIsReportedByRegistryIdAndNotObservedWithoutAPlayer() {
        ticks(1);
        game.inventory.put("minecraft:shield", 1);
        assertEquals(Optional.of(Jdk8.mapOf("minecraft:iron_sword", 1, "minecraft:shield", 1)),
                observe(ObservationKinds.INVENTORY_ITEMS).observedValue());
        game.inventory = null;
        assertEquals(Availability.NOT_OBSERVED, observe(ObservationKinds.INVENTORY_ITEMS).availability());
    }

    @Test
    void engineSignalsAreUnsupportedWithoutAnEngine() {
        ticks(1);
        assertEquals(Optional.of(RejectionReason.UNSUPPORTED_KIND),
                conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick()).rejection());
        assertEquals(Optional.of(RejectionReason.UNSUPPORTED_KIND),
                conductor.observe(ObservationKinds.COMBAT_TARGET, FreshnessPolicy.sameTick()).rejection());
    }

    // --- content detection ---

    @Test
    void registryContentIsPresentOrAbsentOnlyAfterLooking() {
        game.registry.add("ITEM:minecraft:mace");
        assertEquals(ContentResult.Presence.PRESENT, conductor.content(ContentId.item("minecraft:mace")).presence());
        assertEquals(ContentResult.Presence.ABSENT, conductor.content(ContentId.item("minecraft:iron_spear")).presence());
        assertEquals(ContentResult.Presence.UNKNOWN, conductor.content(ContentId.item("not an id")).presence(),
                "a malformed id is UNKNOWN, never ABSENT");
    }

    @Test
    void engineFeaturesFollowTheBuildAndUnknownIdsStayUnknown() {
        assertEquals(ContentResult.Presence.PRESENT, conductor.content(ContentId.engine("ostinato:pvp")).presence());
        assertEquals(ContentResult.Presence.ABSENT, conductor.content(ContentId.engine("ostinato:combat/spear")).presence());
        assertEquals(ContentResult.Presence.UNKNOWN, conductor.content(ContentId.engine("ostinato:teleport")).presence());

        engineClasses.add("baritone.process.CombatSpear");
        assertEquals(ContentResult.Presence.PRESENT, conductor.content(ContentId.engine("ostinato:combat/spear")).presence());
    }

    // --- no engine ---

    @Test
    void discoveryShowsTheCoreAndNoEngine() {
        ticks(1);
        List<CapabilityDescriptor> caps = conductor.discover().capabilities();
        assertEquals(Jdk8.setOf(CoreRuntime.PLAYER, CoreRuntime.WORLD, CoreRuntime.ACTIONS, CoreRuntime.EVENTS,
                CoreRuntime.OVERLAYS, CoreRuntime.CONTENT), caps.stream().map(CapabilityDescriptor::id).collect(Collectors.toSet()));
        CapabilityDescriptor content = caps.stream().filter(c -> c.id().equals(CoreRuntime.CONTENT)).findFirst().get();
        assertTrue(content.detail().contains("engine none"), content.detail());
        assertTrue(content.detail().contains("[ostinato:pvp, ostinato:pve]"), content.detail());
        assertEquals(CapabilityAvailability.AVAILABLE, content.availability());
        assertEquals("none", installed.engine());
    }

    @Test
    void withoutAnEngineMovementIsFreeOnceTheWindowIsQuiet() {
        ticks(WINDOW + 1);
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
    }

    @Test
    void theUseKeyStillBlocksReleaseWithoutAnEngine() {
        game.useKey = true;
        ticks(WINDOW * 3);
        assertNotEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state(), "someone is holding use");
        game.useKey = false;
        ticks(WINDOW + 1);
        assertEquals(ResourceState.AVAILABLE, conductor.resourceStatus().state());
    }

    @Test
    void travelAndCombatAreRejectedWithoutAnEngine() {
        ticks(WINDOW + 1);
        assertEquals(Optional.of(RejectionReason.NO_NAVIGATION), conductor.requestTravel(NavigationGoal.xz(0, 0)).rejection());
        assertEquals(Optional.of(RejectionReason.NO_COMBAT), conductor.requestCombat(CombatGoal.hostiles()).rejection());
    }
}
