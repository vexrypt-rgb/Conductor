package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Typed settings: defaults, saved values, validation, chat commands, the change topic, and the file store. */
class SettingsTest {

    static final Setting<Integer> RANGE = Setting.integer("range", "how far to look", 8, 1, 32);
    static final Setting<Boolean> TRACERS = Setting.bool("tracers", "draw tracers", true);
    static final Setting<String> GREETING = Setting.text("greeting", "what to say", "hello");

    private final Map<ModuleId, Map<String, String>> saved = new HashMap<>();
    private final SettingsStore store = new SettingsStore() {
        public Map<String, String> load(ModuleId m) { return saved.getOrDefault(m, Map.of()); }
        public void save(ModuleId m, Map<String, String> v) { saved.put(m, Map.copyOf(v)); }
    };

    private FakeRuntime runtime;
    private EventsAndCommandsTest.FakeEventPort events;
    private FakeActionPort actions;
    private InMemoryDiagnosticSink sink;
    private ContractService core;
    private ModuleHost host;
    private ModuleContext ctx;
    private final List<String> changes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        saved.put(new ModuleId("tester"), Map.of("range", "12", "tracers", "maybe"));
        runtime = new FakeRuntime();
        runtime.port = new FakePort();
        events = new EventsAndCommandsTest.FakeEventPort();
        actions = new FakeActionPort();
        runtime.eventPort = events;
        runtime.actionPort = actions;
        runtime.quiet();
        sink = new InMemoryDiagnosticSink(10_000);
        ContextTracker contexts = new ContextTracker(sink);
        core = new ContractService(runtime, contexts, sink, new ReleasePolicy(OptionalLong.of(2), OptionalLong.empty()));
        contexts.onInWorld("minecraft:overworld", false, 0);
        host = new ModuleHost(core);
        host.useSettingsStore(store);
        ModuleContext[] holder = new ModuleContext[1];
        host.register("tester", new ConductorModule() {
            public ReleasePolicy releasePolicy() { return ReleasePolicy.none(); }
            public List<Setting<?>> settings() { return List.of(RANGE, TRACERS, GREETING); }
            public void onReady(ModuleContext c) {
                holder[0] = c;
                c.subscribe(Topics.SETTINGS, ch -> changes.add(ch.name() + "=" + ch.value()));
            }
        });
        tick();
        ctx = holder[0];
    }

    private void tick() {
        runtime.tick++;
        core.tick();
        host.tick();
    }

    private void type(String line) {
        events.pending.add(new GameEvent.CommandLine(line));
        tick();
    }

    @Test
    void savedValuesAreUsedAndInvalidOnesFallBackToTheDefault() {
        assertEquals(12, ctx.get(RANGE));
        assertTrue(ctx.get(TRACERS), "'maybe' is not a boolean: default");
        assertEquals("hello", ctx.get(GREETING));
        assertTrue(sink.snapshot().stream().anyMatch(e -> e.type() == DiagnosticEvent.Type.SETTING && e.detail().contains("maybe")));
    }

    @Test
    void thePlayerChangesASettingByCommandAndTheModuleHearsAboutIt() {
        type("+set tester range 20");
        assertEquals(20, ctx.get(RANGE));
        assertEquals("20", saved.get(new ModuleId("tester")).get("range"), "saved at once");
        tick();
        assertEquals(List.of("range=20"), changes);
        type("+set tester greeting good morning");
        assertEquals("good morning", ctx.get(GREETING));
    }

    @Test
    void outOfRangeValuesAreRefused() {
        type("+set tester range 99");
        assertEquals(12, ctx.get(RANGE));
        assertTrue(actions.calls.get(actions.calls.size() - 1).contains("not changed"));
        assertFalse(host.management().setSetting(new ModuleId("tester"), "nope", "1"));
        assertThrows(IllegalArgumentException.class, () -> ctx.set(RANGE, 0));
        assertThrows(IllegalArgumentException.class, () -> ctx.get(Setting.bool("other", "", false)));
    }

    @Test
    void settingsAreListedForAUi() {
        List<SettingInfo> list = host.management().settings(new ModuleId("tester"));
        assertEquals(new SettingInfo("range", "how far to look", "12", "8"), list.get(0));
        type("+settings tester");
        assertTrue(actions.calls.contains("show range = 12 (default 8) how far to look"), actions.calls::toString);
    }

    @Test
    void booleanAndNumberParsing() {
        assertEquals(Optional.of(false), TRACERS.parse("off"));
        assertEquals(Optional.empty(), RANGE.parse("ten"));
        assertThrows(IllegalArgumentException.class, () -> Setting.integer("bad", "", 50, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> Setting.bool("Bad Name", "", true));
    }

    @Test
    void theFileStoreRoundTrips(@TempDir Path dir) {
        FileSettingsStore files = new FileSettingsStore(dir.resolve("config"));
        assertEquals(Map.of(), files.load(new ModuleId("m")));
        files.save(new ModuleId("m"), Map.of("range", "20", "greeting", "hi = there"));
        assertEquals(Map.of("range", "20", "greeting", "hi = there"), files.load(new ModuleId("m")));
    }
}
