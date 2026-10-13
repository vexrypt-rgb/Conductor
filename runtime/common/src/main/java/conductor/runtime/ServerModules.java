package conductor.runtime;

import conductor.contract.OnlinePlayer;
import conductor.contract.ServerCommand;
import conductor.contract.ServerControl;
import conductor.contract.ServerModule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Runs {@link ServerModule}s: the version-independent part. Each version's code supplies a {@link Server} (the live
 * game server) and calls {@link #tick}, {@link #stop} and {@link #chat} from its hooks; this class works out joins,
 * leaves and deaths by comparing the player list from tick to tick, and keeps a module that throws from taking the
 * server down.
 */
public final class ServerModules {

    /** What a version's code supplies: the live server, for every module. Server thread only. */
    public interface Server {
        String minecraftVersion();

        boolean dedicated();

        long tick();

        List<OnlinePlayer> players();

        boolean tell(String player, String message);

        void broadcast(String message);

        void runCommand(String command);

        Optional<String> blockAt(String dimension, int x, int y, int z);
    }

    /** Where this class reports; log4j in the game, a list in tests. */
    public interface Log {
        void info(String message);

        void warn(String message);

        void error(String message, Throwable t);
    }

    /** A module's command, with the module that added it. */
    public record Registered(String moduleId, ServerCommand command) {
    }

    private static final class Entry {
        final String id;
        final ServerModule module;
        volatile boolean off;
        ServerControl control;

        Entry(String id, ServerModule module) {
            this.id = id;
            this.module = module;
        }
    }

    private record Chat(String uuid, String message) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Registered> commands = new ArrayList<>();
    private final Log log;
    private final Queue<Chat> chats = new ConcurrentLinkedQueue<>();
    private Server server;
    private Map<String, OnlinePlayer> seen = Map.of();

    public ServerModules(Map<String, ServerModule> modules, Log log) {
        this.log = log;
        modules.forEach((id, m) -> entries.add(new Entry(id, m)));
        Map<String, String> taken = new LinkedHashMap<>();
        for (Entry e : entries) {
            List<ServerCommand> declared;
            try {
                declared = List.copyOf(e.module.commands());
            } catch (RuntimeException | LinkageError t) {
                fail(e, "commands", t);
                continue;
            }
            for (ServerCommand c : declared) {
                String other = taken.putIfAbsent(c.name(), e.id);
                if (other != null) {
                    log.warn("Conductor server module " + e.id + ": /" + c.name() + " is already added by " + other + "; left out");
                } else {
                    commands.add(new Registered(e.id, c));
                }
            }
        }
    }

    public List<String> moduleIds() {
        return entries.stream().map(e -> e.id).toList();
    }

    /** Every module's commands, first come first served by name. Vanilla's own names are checked by the caller. */
    public List<Registered> commands() {
        return List.copyOf(commands);
    }

    /** True while a server is running with these modules started. */
    public boolean running() {
        return server != null;
    }

    /** Remember a chat message for the end of this tick. Any thread. */
    public void chat(String uuid, String message) {
        if (!entries.isEmpty()) {
            chats.add(new Chat(uuid, message));
        }
    }

    /** End of a server tick: start the modules if this server is new, deliver the tick's events, then tick them. */
    public void tick(Server s) {
        if (entries.isEmpty()) {
            return;
        }
        if (server == null) {
            server = s;
            seen = Map.of();
            chats.clear();
            for (Entry e : entries) {
                e.control = new Control(e.id, s);
            }
            log.info("Conductor server modules started on " + (s.dedicated() ? "a dedicated server" : "a single-player server")
                    + ": " + moduleIds());
            each("started", m -> m.module.started(m.control));
        }
        List<OnlinePlayer> now = s.players();
        Map<String, OnlinePlayer> current = new LinkedHashMap<>();
        for (OnlinePlayer p : now) {
            current.put(p.uuid(), p);
        }
        for (OnlinePlayer p : now) {
            if (!seen.containsKey(p.uuid())) {
                each("joined", m -> m.module.joined(m.control, p));
            }
        }
        for (Chat c; (c = chats.poll()) != null; ) {
            OnlinePlayer p = current.getOrDefault(c.uuid(), seen.get(c.uuid()));
            if (p != null) {
                String text = c.message();
                each("chat", m -> m.module.chat(m.control, p, text));
            }
        }
        for (OnlinePlayer p : now) {
            OnlinePlayer before = seen.get(p.uuid());
            if (before != null && before.alive() && !p.alive()) {
                each("died", m -> m.module.died(m.control, p));
            }
        }
        for (OnlinePlayer p : seen.values()) {
            if (!current.containsKey(p.uuid())) {
                each("left", m -> m.module.left(m.control, p));
            }
        }
        seen = current;
        each("tick", m -> m.module.tick(m.control));
    }

    /** The server is stopping: tell the modules, and forget it, so the next server starts them again. */
    public void stop() {
        if (server == null) {
            return;
        }
        each("stopping", m -> m.module.stopping(m.control));
        server = null;
        seen = Map.of();
        chats.clear();
        for (Entry e : entries) {
            e.control = null;
        }
    }

    /**
     * Run a module's command for {@code call}, on the server thread. Returns the reply (null: none).
     *
     * @throws ServerCommand.Failure an error answer for the sender: the handler's own, or that the module is off
     */
    public String run(Registered r, Server s, ServerCommand.Call call) {
        Entry e = entries.stream().filter(x -> x.id.equals(r.moduleId())).findFirst().orElseThrow();
        if (e.off) {
            throw ServerCommand.fail("/" + r.command().name() + " is off: its module (" + e.id + ") failed earlier; see the server log");
        }
        ServerControl control = e.control != null ? e.control : new Control(e.id, s);
        try {
            return r.command().handler().run(control, call);
        } catch (ServerCommand.Failure f) {
            throw f;
        } catch (RuntimeException | LinkageError t) {
            fail(e, "/" + r.command().name(), t);
            throw ServerCommand.fail("/" + r.command().name() + " failed; see the server log");
        }
    }

    /** Find a player in {@link Server#players()} by name, ignoring case. */
    static Optional<OnlinePlayer> byName(List<OnlinePlayer> players, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return players.stream().filter(p -> p.name().toLowerCase(Locale.ROOT).equals(lower)).findFirst();
    }

    private void each(String what, Consumer<Entry> call) {
        for (Entry e : entries) {
            if (e.off) {
                continue;
            }
            try {
                call.accept(e);
            } catch (RuntimeException | LinkageError t) {
                fail(e, what, t);
            }
        }
    }

    private void fail(Entry e, String what, Throwable t) {
        e.off = true;
        log.error("Conductor server module " + e.id + " threw in " + what + "; it is off until the game restarts."
                + " The server keeps running", t);
    }

    /** One module's view of the server: the same server, with its own log prefix. */
    private final class Control implements ServerControl {
        private final String id;
        private final Server s;

        Control(String id, Server s) {
            this.id = id;
            this.s = s;
        }

        @Override
        public String minecraftVersion() {
            return s.minecraftVersion();
        }

        @Override
        public boolean dedicated() {
            return s.dedicated();
        }

        @Override
        public long tick() {
            return s.tick();
        }

        @Override
        public List<OnlinePlayer> players() {
            return s.players();
        }

        @Override
        public Optional<OnlinePlayer> player(String name) {
            return byName(s.players(), name);
        }

        @Override
        public boolean tell(String player, String message) {
            return s.tell(player, message);
        }

        @Override
        public void broadcast(String message) {
            s.broadcast(message);
        }

        @Override
        public void runCommand(String command) {
            String c = command.startsWith("/") ? command.substring(1) : command;
            log.info("Conductor server module " + id + " runs /" + c);
            s.runCommand(c);
        }

        @Override
        public Optional<String> blockAt(String dimension, int x, int y, int z) {
            return s.blockAt(dimension, x, y, z);
        }

        @Override
        public void log(String message) {
            log.info("[" + id + "] " + message);
        }
    }

    // --- the game's single instance ---

    private static volatile Path modulesDir;
    private static volatile ClassLoader loader;
    private static volatile ServerModules instance;

    /** Where to look for modules; called by the boot shim before the game starts. */
    public static void configure(Path dir, ClassLoader gameLoader) {
        modulesDir = dir;
        loader = gameLoader;
    }

    /** The game's server modules, found the first time they are needed. Never throws. */
    public static ServerModules get() {
        ServerModules m = instance;
        if (m != null) {
            return m;
        }
        synchronized (ServerModules.class) {
            if (instance == null) {
                Log log = log4j();
                Map<String, ServerModule> found = Map.of();
                try {
                    ModuleDiscovery.Found f = ModuleDiscovery.discoverServer(modulesDir, loader != null ? loader
                            : ServerModules.class.getClassLoader());
                    f.problems().forEach(p -> log.warn("Conductor server module problem: " + p));
                    found = f.server();
                } catch (Throwable t) {
                    log.error("Conductor could not look for server modules; continuing without them", t);
                }
                instance = new ServerModules(found, log);
                if (!found.isEmpty()) {
                    log.info("Conductor server modules found: " + found.keySet());
                }
            }
            return instance;
        }
    }

    private static Log log4j() {
        org.apache.logging.log4j.Logger l = org.apache.logging.log4j.LogManager.getLogger("conductor");
        return new Log() {
            public void info(String message) {
                l.info(message);
            }

            public void warn(String message) {
                l.warn(message);
            }

            public void error(String message, Throwable t) {
                l.error(message, t);
            }
        };
    }
}
