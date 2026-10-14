package conductor.runtime;

import conductor.contract.Availability;
import conductor.contract.BlockPoint;
import conductor.contract.CapabilityDescriptor;
import conductor.contract.Conductor;
import conductor.contract.EffectInfo;
import conductor.contract.EntityInfo;
import conductor.contract.FreshnessPolicy;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.contract.ObservationKind;
import conductor.contract.ObservationKinds;
import conductor.contract.PlayerMotion;
import conductor.contract.PlayerStatus;
import conductor.contract.ReleasePolicy;
import conductor.contract.SlotInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The runtime's player, world and action surface: routing, NOT_OBSERVED without a player, discovery. */
class PlayerAdapterTest {

    static final class FakePlayerAccess implements PlayerAccess {
        boolean present = true;
        final List<String> calls = new ArrayList<>();
        double lastRadius;

        public PlayerStatus status() { return present ? new PlayerStatus(20, 0, 20, 18, 5, 300, true, false, false, false, false, "survival") : null; }
        public PlayerMotion motion() { return present ? new PlayerMotion(0.5, 64, 0.5, 0, -0.08, 0, 90, 10) : null; }
        public List<EntityInfo> entities(double radius, int max) {
            lastRadius = radius;
            return present ? Jdk8.listOf(new EntityInfo(7, "minecraft:zombie", "Zombie", 3, 64, 0, 2.5, OptionalDouble.of(20), false, true)) : null;
        }
        public List<SlotInfo> slots() { return present ? Jdk8.listOf(new SlotInfo(0, "minecraft:iron_sword", 1, 3, 250)) : null; }
        public Integer selectedSlot() { return present ? 0 : null; }
        public List<EffectInfo> effects() { return present ? Jdk8.listOf() : null; }
        public Optional<String> blockAt(BlockPoint at) { return Optional.of("minecraft:grass_block"); }
        public List<BlockPoint> findBlocks(Set<String> ids, BlockPoint center, int radius, int limit) { return Jdk8.listOf(center); }
        public Optional<String> selectHotbar(int slot) { calls.add("hotbar " + slot); return Optional.empty(); }
        public Optional<String> look(double yaw, double pitch) { calls.add("look"); return Optional.empty(); }
        public Optional<String> attack(int id) { calls.add("attack " + id); return Optional.empty(); }
        public Optional<String> useItem(boolean offhand) { calls.add("use"); return Optional.empty(); }
        public Optional<String> sendChat(String m) { calls.add("chat " + m); return Optional.empty(); }
        public Optional<String> showMessage(String t) { calls.add("show " + t); return Optional.empty(); }
    }

    private final FakeGameAccess game = new FakeGameAccess();
    private final FakePlayerAccess player = new FakePlayerAccess();
    private Conductor conductor;

    @BeforeEach
    void setUp() {
        ConductorRuntime.Installed installed = ConductorRuntime.install(new InMemoryDiagnosticSink(1000),
                new ReleasePolicy(OptionalLong.of(2), OptionalLong.empty()), game, player, null, null, null,
                new EngineFeatures(n -> false));
        conductor = installed.service();
        for (int i = 0; i < 3; i++) {
            installed.tick().run();
        }
    }

    private <T> Optional<T> read(ObservationKind<T> kind) {
        return conductor.observe(kind, FreshnessPolicy.sameTick()).observation().flatMap(o -> o.observedValue());
    }

    @Test
    void everyNewObservationIsRoutedToThePlayerAccess() {
        assertEquals(20.0, read(ObservationKinds.PLAYER_STATUS).get().health());
        assertEquals(90.0, read(ObservationKinds.PLAYER_MOTION).get().yaw());
        assertEquals("minecraft:zombie", read(ObservationKinds.NEARBY_ENTITIES).get().get(0).type());
        assertEquals(CoreRuntime.ENTITY_RADIUS, player.lastRadius);
        assertEquals(250, read(ObservationKinds.INVENTORY_SLOTS).get().get(0).maxDamage());
        assertEquals(Optional.of(0), read(ObservationKinds.SELECTED_SLOT));
        assertEquals(Optional.of(Jdk8.listOf()), read(ObservationKinds.ACTIVE_EFFECTS));
    }

    @Test
    void withoutAPlayerReadsAreNotObservedNotZero() {
        player.present = false;
        assertEquals(Availability.NOT_OBSERVED,
                conductor.observe(ObservationKinds.PLAYER_STATUS, FreshnessPolicy.sameTick()).observation().get().availability());
    }

    @Test
    void actionsAndBlockQueriesReachTheGameAndDiscoveryListsThem() {
        assertTrue(conductor.selectHotbar(2).isAccepted());
        assertTrue(conductor.sendChat("/help").isAccepted());
        assertEquals(Jdk8.listOf("hotbar 2", "chat /help"), player.calls);
        assertEquals(Optional.of("minecraft:grass_block"), conductor.blockAt(new BlockPoint(0, 63, 0)).block());
        CapabilityDescriptor actions = conductor.discover().capabilities().stream()
                .filter(c -> c.id().equals(CoreRuntime.ACTIONS)).findFirst().get();
        assertEquals(CoreRuntime.ACTION_OPERATIONS, actions.operations());
    }
}
