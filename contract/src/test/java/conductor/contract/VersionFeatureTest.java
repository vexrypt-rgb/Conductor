package conductor.contract;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import static conductor.contract.ContentResult.Presence.ABSENT;
import static conductor.contract.ContentResult.Presence.PRESENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Version-agnostic modules through content detection: one module declares optional features by the content they
 * need; each runtime answers from its own game and engine. The owner's example: spear tactics exist on 1.21.11
 * (spear items + engine spear combat) and are simply off on 1.21.4, while the rest of the module works on both.
 */
class VersionFeatureTest {

    static final ContentId IRON_SPEAR = ContentId.item("minecraft:iron_spear");
    static final ContentId ENGINE_SPEAR = ContentId.engine("ostinato:combat/spear");
    static final ContentId MACE = ContentId.item("minecraft:mace");
    static final ContentId ENGINE_MACE = ContentId.engine("ostinato:combat/mace");

    /** A combat-like module with a core part and two optional parts. */
    static final class CombatLikeModule implements ConductorModule {
        final List<String> did = new ArrayList<>();
        ModuleContext ctx;

        @Override
        public ReleasePolicy releasePolicy() {
            return new ReleasePolicy(OptionalLong.of(3), OptionalLong.empty());
        }

        @Override
        public List<ModuleFeature> features() {
            return List.of(
                    ModuleFeature.of("spear", "spear kit and lunge tactics", IRON_SPEAR, ENGINE_SPEAR),
                    ModuleFeature.of("mace", "mace smash tactics", MACE, ENGINE_MACE));
        }

        @Override
        public void onReady(ModuleContext c) {
            ctx = c;
            did.add("melee"); // the core part runs everywhere
            if (c.feature("spear").enabled()) {
                did.add("spear");
            }
            if (c.feature("mace").enabled()) {
                did.add("mace");
            }
        }
    }

    private static CombatLikeModule runOn(FakeRuntime runtime) {
        InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(1000);
        ContextTracker contexts = new ContextTracker(sink);
        ContractService core = new ContractService(runtime, contexts, sink, new ReleasePolicy(OptionalLong.of(3), OptionalLong.empty()));
        ModuleHost host = new ModuleHost(core);
        CombatLikeModule module = new CombatLikeModule();
        host.register("combat", module);
        contexts.onInWorld("minecraft:overworld", false, 0);
        core.tick();
        host.tick();
        return module;
    }

    @Test
    void sameModuleTurnsSpearTacticsOnOrOffByWhatTheGameHas() {
        FakeRuntime like1_21_11 = new FakeRuntime();
        like1_21_11.content.put(IRON_SPEAR, PRESENT);
        like1_21_11.content.put(ENGINE_SPEAR, PRESENT);
        like1_21_11.content.put(MACE, PRESENT);
        like1_21_11.content.put(ENGINE_MACE, PRESENT);

        FakeRuntime like1_21_4 = new FakeRuntime();
        like1_21_4.content.put(IRON_SPEAR, ABSENT);   // no spears in the game
        like1_21_4.content.put(ENGINE_SPEAR, ABSENT);
        like1_21_4.content.put(MACE, PRESENT);        // the item exists...
        like1_21_4.content.put(ENGINE_MACE, ABSENT);  // ...but this engine build has no mace combat

        assertEquals(List.of("melee", "spear", "mace"), runOn(like1_21_11).did);
        CombatLikeModule older = runOn(like1_21_4);
        assertEquals(List.of("melee"), older.did, "the rest of the module still runs");
        assertTrue(older.ctx.feature("spear").reason().contains("ABSENT"));
        assertTrue(older.ctx.feature("mace").reason().contains("engine:ostinato:combat/mace ABSENT"),
                "content present in the game is not enough when the engine lacks support");
    }

    @Test
    void unknownContentKeepsAFeatureOffButIsNotReportedAsAbsent() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.content.put(IRON_SPEAR, PRESENT); // engine spear left unconfigured -> UNKNOWN
        CombatLikeModule m = runOn(runtime);

        FeatureStatus spear = m.ctx.feature("spear");
        assertFalse(spear.enabled());
        assertTrue(spear.reason().contains("UNKNOWN"));
        assertFalse(spear.reason().contains("ABSENT"));
    }

    @Test
    void undeclaredFeaturesAreOff() {
        CombatLikeModule m = runOn(new FakeRuntime());
        assertFalse(m.ctx.feature("trident").enabled());
        assertEquals(2, m.ctx.features().size());
    }

    @Test
    void contentLookupsOffTheGameThreadAreUnknown() throws InterruptedException {
        FakeRuntime runtime = new FakeRuntime();
        runtime.content.put(IRON_SPEAR, PRESENT);
        CombatLikeModule m = runOn(runtime);
        AtomicReference<ContentResult> r = new AtomicReference<>();
        Thread t = new Thread(() -> r.set(m.ctx.content(IRON_SPEAR)));
        t.start();
        t.join();
        assertEquals(ContentResult.Presence.UNKNOWN, r.get().presence());
        assertEquals(ContentResult.Presence.PRESENT, m.ctx.content(IRON_SPEAR).presence());
    }

    @Test
    void featureDecisionsAreRecordedInTheSink() {
        FakeRuntime runtime = new FakeRuntime();
        InMemoryDiagnosticSink sink = new InMemoryDiagnosticSink(1000);
        ContextTracker contexts = new ContextTracker(sink);
        ContractService core = new ContractService(runtime, contexts, sink, new ReleasePolicy(OptionalLong.of(3), OptionalLong.empty()));
        ModuleHost host = new ModuleHost(core);
        host.register("combat", new CombatLikeModule());
        contexts.onInWorld("minecraft:overworld", false, 0);
        host.tick();

        assertEquals(2, sink.snapshot().stream().filter(e -> e.type() == DiagnosticEvent.Type.MODULE_FEATURE).count());
        assertTrue(sink.snapshot().stream().anyMatch(e -> e.detail().startsWith("spear OFF")));
        assertEquals(Optional.empty(), Optional.<String>empty());
    }
}
