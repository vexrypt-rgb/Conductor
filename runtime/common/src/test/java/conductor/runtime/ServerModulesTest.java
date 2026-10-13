package conductor.runtime;

import conductor.contract.OnlinePlayer;
import conductor.contract.ServerCommand;
import conductor.contract.ServerControl;
import conductor.contract.ServerModule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The version-independent part of server modules: events from player-list changes, and failing modules. */
class ServerModulesTest {

    /** A server whose players the test sets before each tick. */
    static final class FakeServer implements ServerModules.Server {
        List<OnlinePlayer> players = new ArrayList<>();
        final List<String> ran = new ArrayList<>();
        final List<String> said = new ArrayList<>();
        long tick;

        public String minecraftVersion() {
            return "1.21.4";
        }

        public boolean dedicated() {
            return true;
        }

        public long tick() {
            return tick;
        }

        public List<OnlinePlayer> players() {
            return players;
        }

        public boolean tell(String player, String message) {
            said.add(player + ": " + message);
            return true;
        }

        public void broadcast(String message) {
            said.add("all: " + message);
        }

        public void runCommand(String command) {
            ran.add(command);
        }

        public Optional<String> blockAt(String dimension, int x, int y, int z) {
            return Optional.of("minecraft:stone");
        }
    }

    static final class ListLog implements ServerModules.Log {
        final List<String> lines = new ArrayList<>();

        public void info(String message) {
            lines.add("INFO " + message);
        }

        public void warn(String message) {
            lines.add("WARN " + message);
        }

        public void error(String message, Throwable t) {
            lines.add("ERROR " + message);
        }
    }

    /** Writes each event into a shared list. */
    static class Recorder implements ServerModule {
        final String id;
        final List<String> events;
        final List<ServerCommand> commands;

        Recorder(String id, List<String> events, ServerCommand... commands) {
            this.id = id;
            this.events = events;
            this.commands = List.of(commands);
        }

        public List<ServerCommand> commands() {
            return commands;
        }

        public void started(ServerControl s) {
            events.add(id + " started");
        }

        public void tick(ServerControl s) {
            events.add(id + " tick " + s.tick());
        }

        public void joined(ServerControl s, OnlinePlayer p) {
            events.add(id + " joined " + p.name());
        }

        public void left(ServerControl s, OnlinePlayer p) {
            events.add(id + " left " + p.name());
        }

        public void died(ServerControl s, OnlinePlayer p) {
            events.add(id + " died " + p.name());
        }

        public void chat(ServerControl s, OnlinePlayer p, String message) {
            events.add(id + " chat " + p.name() + " " + message);
        }

        public void stopping(ServerControl s) {
            events.add(id + " stopping");
        }
    }

    private static OnlinePlayer player(String name, double health) {
        return new OnlinePlayer(name, "uuid-" + name, "minecraft:overworld", 0, 64, 0, health, false);
    }

    private static Map<String, ServerModule> modules(ServerModule... ms) {
        Map<String, ServerModule> map = new LinkedHashMap<>();
        for (int i = 0; i < ms.length; i++) {
            map.put(ms[i] instanceof Recorder r ? r.id : "m" + i, ms[i]);
        }
        return map;
    }

    private static ServerModules.Registered command(ServerModules sm, String name) {
        return sm.commands().stream().filter(r -> r.command().name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void eventsComeInOrderFromPlayerListChanges() {
        List<String> events = new ArrayList<>();
        ListLog log = new ListLog();
        ServerModules sm = new ServerModules(modules(new Recorder("a", events)), log);
        FakeServer s = new FakeServer();

        s.tick = 1;
        sm.tick(s);
        assertEquals(List.of("a started", "a tick 1"), events);
        assertTrue(log.lines.contains("INFO Conductor server modules started on a dedicated server: [a]"), log.lines::toString);

        events.clear();
        s.tick = 2;
        s.players = List.of(player("Alex", 20));
        sm.chat("uuid-Alex", "hello");
        sm.tick(s);
        assertEquals(List.of("a joined Alex", "a chat Alex hello", "a tick 2"), events);

        events.clear();
        s.tick = 3;
        s.players = List.of(player("Alex", 0), player("Steve", 20));
        sm.tick(s);
        assertEquals(List.of("a joined Steve", "a died Alex", "a tick 3"), events);

        events.clear();
        s.tick = 4;
        s.players = List.of(player("Alex", 0));
        sm.chat("uuid-Steve", "bye");
        sm.tick(s);
        // Steve's last message still arrives, from the tick he left in, before he leaves
        assertEquals(List.of("a chat Steve bye", "a left Steve", "a tick 4"), events);

        events.clear();
        s.tick = 5;
        s.players = List.of(player("Alex", 20));
        sm.tick(s);
        assertEquals(List.of("a tick 5"), events, "respawning is not a join");
    }

    @Test
    void aModuleThatThrowsIsTurnedOffAndTheOthersContinue() {
        List<String> events = new ArrayList<>();
        ListLog log = new ListLog();
        Recorder bad = new Recorder("bad", events, ServerCommand.of("boom", "", (s, c) -> "never")) {
            @Override
            public void joined(ServerControl s, OnlinePlayer p) {
                throw new IllegalStateException("broken");
            }
        };
        ServerModules sm = new ServerModules(modules(bad, new Recorder("good", events)), log);
        FakeServer s = new FakeServer();
        s.players = List.of(player("Alex", 20));
        sm.tick(s);
        assertEquals(List.of("bad started", "good started", "good joined Alex", "good tick 0"), events);
        assertTrue(log.lines.stream().anyMatch(l -> l.startsWith("ERROR Conductor server module bad threw in joined")),
                log.lines::toString);

        ServerModules.Registered boom = command(sm, "boom");
        ServerCommand.Failure f = assertThrows(ServerCommand.Failure.class,
                () -> sm.run(boom, s, new ServerCommand.Call("boom", List.of(), "Alex", Optional.empty())));
        assertEquals("/boom is off: its module (bad) failed earlier; see the server log", f.getMessage());
    }

    @Test
    void aCommandThatThrowsTurnsItsModuleOffButAFailureIsJustAnAnswer() {
        List<String> events = new ArrayList<>();
        ListLog log = new ListLog();
        Recorder m = new Recorder("m", events,
                ServerCommand.of("echo", "", (s, c) -> s.tick() + " " + String.join(",", c.args())),
                ServerCommand.of("nope", "", (s, c) -> {
                    throw ServerCommand.fail("not now");
                }),
                ServerCommand.of("crash", "", (s, c) -> {
                    throw new NullPointerException();
                }));
        ServerModules sm = new ServerModules(modules(m), log);
        FakeServer s = new FakeServer();
        s.tick = 7;
        sm.tick(s);
        ServerCommand.Call call = new ServerCommand.Call("echo", List.of("x", "y"), "Server", Optional.empty());
        assertEquals("7 x,y", sm.run(command(sm, "echo"), s, call));

        ServerCommand.Failure nope = assertThrows(ServerCommand.Failure.class,
                () -> sm.run(command(sm, "nope"), s, call));
        assertEquals("not now", nope.getMessage());
        assertEquals("7 x,y", sm.run(command(sm, "echo"), s, call), "a Failure does not turn the module off");

        ServerCommand.Failure crash = assertThrows(ServerCommand.Failure.class,
                () -> sm.run(command(sm, "crash"), s, call));
        assertEquals("/crash failed; see the server log", crash.getMessage());
        assertThrows(ServerCommand.Failure.class, () -> sm.run(command(sm, "echo"), s, call));
        events.clear();
        sm.tick(s);
        assertEquals(List.of(), events, "the module gets no more events");
    }

    @Test
    void aCommandNameIsAddedOnceAndModulesThatFailToListThemAreOff() {
        List<String> events = new ArrayList<>();
        ListLog log = new ListLog();
        ServerCommand.Handler h = (s, c) -> null;
        Recorder first = new Recorder("first", events, ServerCommand.of("home", "", h));
        Recorder second = new Recorder("second", events, ServerCommand.of("home", "", h), ServerCommand.of("spawn", "", h));
        ServerModule broken = new ServerModule() {
            @Override
            public List<ServerCommand> commands() {
                throw new IllegalStateException("no");
            }
        };
        ServerModules sm = new ServerModules(modules(first, second, broken), log);
        assertEquals(List.of("first/home", "second/spawn"),
                sm.commands().stream().map(r -> r.moduleId() + "/" + r.command().name()).toList());
        assertTrue(log.lines.contains("WARN Conductor server module second: /home is already added by first; left out"),
                log.lines::toString);
        assertTrue(log.lines.stream().anyMatch(l -> l.startsWith("ERROR Conductor server module m2 threw in commands")),
                log.lines::toString);
    }

    @Test
    void stoppingTellsTheModulesAndTheNextServerStartsThemAgain() {
        List<String> events = new ArrayList<>();
        ServerModules sm = new ServerModules(modules(new Recorder("a", events)), new ListLog());
        FakeServer s = new FakeServer();
        s.players = List.of(player("Alex", 20));
        sm.tick(s);
        assertTrue(sm.running());
        sm.stop();
        assertFalse(sm.running());
        sm.stop();
        assertEquals(List.of("a started", "a joined Alex", "a tick 0", "a stopping"), events, "stop is once");

        events.clear();
        sm.chat("uuid-Alex", "from the old server");
        sm.tick(s);
        assertEquals(List.of("a started", "a joined Alex", "a tick 0"), events, "a new server: fresh joins, old chat dropped");
    }

    @Test
    void theControlStripsTheSlashAndFindsPlayersByName() {
        ListLog log = new ListLog();
        List<ServerControl> controls = new ArrayList<>();
        ServerModule m = new ServerModule() {
            @Override
            public void started(ServerControl s) {
                controls.add(s);
            }
        };
        Map<String, ServerModule> map = new LinkedHashMap<>();
        map.put("greeter", m);
        ServerModules sm = new ServerModules(map, log);
        FakeServer s = new FakeServer();
        s.players = List.of(player("Alex", 20));
        sm.tick(s);
        ServerControl c = controls.get(0);
        c.runCommand("/time set noon");
        c.runCommand("say hi");
        assertEquals(List.of("time set noon", "say hi"), s.ran);
        assertTrue(log.lines.contains("INFO Conductor server module greeter runs /time set noon"), log.lines::toString);
        c.log("hello");
        assertTrue(log.lines.contains("INFO [greeter] hello"));
        assertEquals("Alex", c.player("alex").orElseThrow().name());
        assertTrue(c.player("Steve").isEmpty());
        assertEquals(Optional.of("minecraft:stone"), c.blockAt("minecraft:overworld", 0, 0, 0));
    }

    @Test
    void withNoModulesNothingHappens() {
        ServerModules sm = new ServerModules(Map.of(), new ListLog());
        FakeServer s = new FakeServer();
        sm.chat("x", "y");
        sm.tick(s);
        assertFalse(sm.running());
        assertTrue(sm.commands().isEmpty());
    }

    @Test
    void commandNamesAreChecked() {
        ServerCommand.Handler h = (s, c) -> null;
        assertThrows(IllegalArgumentException.class, () -> ServerCommand.of("Home", "", h));
        assertThrows(IllegalArgumentException.class, () -> ServerCommand.of("", "", h));
        assertThrows(IllegalArgumentException.class, () -> ServerCommand.of("a b", "", h));
        assertThrows(IllegalArgumentException.class, () -> ServerCommand.of("x".repeat(33), "", h));
        assertEquals("ruby-home_2", ServerCommand.of("ruby-home_2", null, h).name());
        assertEquals("", ServerCommand.of("ok", null, h).help());
        assertTrue(ServerCommand.operator("op", "", h).operator());
    }
}
