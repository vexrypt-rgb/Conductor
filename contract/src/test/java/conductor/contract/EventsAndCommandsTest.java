package conductor.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Game events become topic messages; chat commands reach the module that registered them; local messages. */
class EventsAndCommandsTest {

    /** Test event port: tests add events; the host drains them. */
    static final class FakeEventPort implements EventPort {
        final List<GameEvent> pending = new ArrayList<>();
        String prefix;

        public List<GameEvent> drain() {
            List<GameEvent> out = List.copyOf(pending);
            pending.clear();
            return out;
        }

        public void interceptCommands(String prefix) {
            this.prefix = prefix;
        }
    }

    private FakeRuntime runtime;
    private FakeEventPort events;
    private FakeActionPort actions;
    private InMemoryDiagnosticSink sink;
    private ContractService core;
    private ModuleHost host;
    private final List<String> log = new ArrayList<>();
    private ModuleContext ctx;

    @BeforeEach
    void setUp() {
        runtime = new FakeRuntime();
        runtime.port = new FakePort();
        events = new FakeEventPort();
        actions = new FakeActionPort();
        runtime.eventPort = events;
        runtime.actionPort = actions;
        runtime.quiet();
        sink = new InMemoryDiagnosticSink(10_000);
        ContextTracker contexts = new ContextTracker(sink);
        core = new ContractService(runtime, contexts, sink, new ReleasePolicy(OptionalLong.of(2), OptionalLong.empty()));
        contexts.onInWorld("minecraft:overworld", false, 0);
        host = new ModuleHost(core);
        ModuleContext[] holder = new ModuleContext[1];
        host.register("tester", new ConductorModule() {
            public ReleasePolicy releasePolicy() { return ReleasePolicy.none(); }
            public void onReady(ModuleContext c) {
                holder[0] = c;
                c.subscribe(Topics.BLOCK_CHANGES, b -> log.add("block " + b.block()));
                c.subscribe(Topics.CHUNKS, ch -> log.add("chunk " + ch.chunkX() + " " + ch.loaded()));
                c.subscribe(Topics.DEATHS, d -> log.add("died"));
                c.subscribe(Topics.CHAT, m -> log.add("chat " + m.sender().orElse("system") + ": " + m.text()));
                c.command("kit", "check the kit", call -> log.add("kit " + call.args()));
                c.command("boom", "throws", call -> { throw new IllegalStateException("boom"); });
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
    void theHostAsksTheRuntimeToInterceptItsPrefix() {
        assertEquals(ModuleHost.DEFAULT_COMMAND_PREFIX, events.prefix);
    }

    @Test
    void gameEventsArriveAsTopicMessagesOnTheGameThread() {
        events.pending.add(new GameEvent.BlockChange(new BlockPoint(1, 2, 3), "minecraft:stone"));
        events.pending.add(new GameEvent.ChunkChange(4, 5, true));
        events.pending.add(new GameEvent.ChatMessage("hi", Optional.of("Alex")));
        events.pending.add(new GameEvent.PlayerDeath());
        tick();
        assertEquals(List.of("block minecraft:stone", "chunk 4 true", "chat Alex: hi", "died"), log);
    }

    @Test
    void aTypedCommandReachesItsModuleWithArguments() {
        type("+kit check now");
        assertEquals(List.of("kit [check, now]"), log);
    }

    @Test
    void commandNamesAreFirstComeFirstServed() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ctx.command("help", "", c -> { }));
        assertTrue(e.getMessage().contains("Conductor"));
        assertThrows(IllegalArgumentException.class, () -> ctx.command("KIT", "", c -> { }));
        assertThrows(IllegalArgumentException.class, () -> ctx.command("two words", "", c -> { }));
    }

    @Test
    void unknownCommandsAreAnsweredLocally() {
        type("+nope");
        assertEquals(List.of("show unknown command nope; +help lists them"), actions.calls);
    }

    @Test
    void builtInCommandsManageModules() {
        type("+disable tester");
        assertEquals(Optional.of(ModuleState.DISABLED), host.state(new ModuleId("tester")));
        type("+kit");
        assertTrue(actions.calls.get(actions.calls.size() - 1).startsWith("show unknown command kit"),
                "a disabled module's commands are gone: " + actions.calls);
        type("+enable tester");
        tick();
        assertEquals(Optional.of(ModuleState.RUNNING), host.state(new ModuleId("tester")));
        type("+kit again");
        assertEquals("kit [again]", log.get(log.size() - 1));
    }

    @Test
    void aThrowingCommandStopsOnlyItsModule() {
        type("+boom");
        assertEquals(Optional.of(ModuleState.FAILED), host.state(new ModuleId("tester")));
        type("+modules");
        assertTrue(actions.calls.contains("show tester: FAILED"), actions.calls::toString);
    }

    @Test
    void moduleMessagesAreLabelledLimitedAndNeedNoLease() {
        assertTrue(ctx.showMessage("ready").isAccepted());
        assertEquals("show [tester] ready", actions.calls.get(0));
        for (int i = 1; i < ContractService.MAX_MESSAGES_PER_TICK; i++) {
            ctx.showMessage("line " + i);
        }
        assertEquals(RejectionReason.RATE_LIMITED, ctx.showMessage("one too many").rejection());
        assertEquals(RejectionReason.INVALID_ARGUMENT, ctx.showMessage("").rejection());
    }
}
