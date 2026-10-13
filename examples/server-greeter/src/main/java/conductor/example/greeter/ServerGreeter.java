package conductor.example.greeter;

import conductor.contract.OnlinePlayer;
import conductor.contract.ServerCommand;
import conductor.contract.ServerControl;
import conductor.contract.ServerModule;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A small server module that uses every part of {@link ServerControl}: it welcomes players and gives them bread
 * (a console command), answers {@code ping} in chat, announces deaths with where they happened, and adds five
 * {@code /} commands. It runs wherever the server runs: a dedicated server, or the world a single-player game hosts.
 * <p>
 * Imports only {@code conductor.contract}: the same jar runs on every supported version.
 */
public final class ServerGreeter implements ServerModule {

    /** Deaths this server session, by player name. Server thread only, like every call into this class. */
    private final Map<String, Integer> deaths = new TreeMap<>();

    @Override
    public List<ServerCommand> commands() {
        return List.of(
                ServerCommand.of("greet", "/greet [player]: say hello, to yourself or to another player", this::greet),
                ServerCommand.of("whereami", "/whereami: your dimension, position and the block under you", this::whereAmI),
                ServerCommand.of("online", "/online: who is online", (s, c) -> online(s)),
                ServerCommand.of("deaths", "/deaths: deaths since the server started", (s, c) -> deaths()),
                ServerCommand.operator("blockat", "/blockat <dimension> <x> <y> <z>: the block there, if its chunk is loaded",
                        this::blockAt));
    }

    @Override
    public void started(ServerControl server) {
        server.log("started on Minecraft " + server.minecraftVersion() + (server.dedicated() ? ", dedicated server" : ", single-player"));
    }

    @Override
    public void joined(ServerControl server, OnlinePlayer p) {
        server.tell(p.name(), "Welcome, " + p.name() + "! This server runs Minecraft " + server.minecraftVersion()
                + ". Try /greet, /whereami, /online and /deaths.");
        server.broadcast(p.name() + " joined; " + server.players().size() + " online");
        server.log(p.name() + " joined in " + where(server, p));
        server.runCommand("give " + p.name() + " minecraft:bread 3");
    }

    @Override
    public void chat(ServerControl server, OnlinePlayer p, String message) {
        server.log("chat from " + p.name() + ": " + message);
        if (message.trim().equalsIgnoreCase("ping")) {
            server.broadcast("pong (tick " + server.tick() + ")");
        }
    }

    @Override
    public void died(ServerControl server, OnlinePlayer p) {
        int n = deaths.merge(p.name(), 1, Integer::sum);
        String text = p.name() + " died in " + where(server, p) + " (death " + n + ")";
        server.broadcast(text);
        server.log(text);
    }

    @Override
    public void left(ServerControl server, OnlinePlayer p) {
        server.log(p.name() + " left; " + server.players().size() + " online");
    }

    @Override
    public void tick(ServerControl server) {
        if (server.tick() % 1200 == 0 && !server.players().isEmpty()) {
            server.log("tick " + server.tick() + ": " + online(server));
        }
    }

    @Override
    public void stopping(ServerControl server) {
        server.log("stopping; " + deaths());
    }

    private String greet(ServerControl server, ServerCommand.Call call) {
        if (call.args().isEmpty()) {
            return "Hello, " + call.sender() + "!";
        }
        String to = call.args().get(0);
        if (!server.tell(to, call.sender() + " says hello!")) {
            throw ServerCommand.fail(to + " is not online");
        }
        return "Said hello to " + to;
    }

    private String whereAmI(ServerControl server, ServerCommand.Call call) {
        OnlinePlayer p = call.player().orElseThrow(() -> ServerCommand.fail("Only a player can use /whereami"));
        return "You are in " + where(server, p);
    }

    private String blockAt(ServerControl server, ServerCommand.Call call) {
        if (call.args().size() != 4) {
            throw ServerCommand.fail("Usage: /blockat <dimension> <x> <y> <z>");
        }
        int x, y, z;
        try {
            x = Integer.parseInt(call.args().get(1));
            y = Integer.parseInt(call.args().get(2));
            z = Integer.parseInt(call.args().get(3));
        } catch (NumberFormatException e) {
            throw ServerCommand.fail("x, y and z must be whole numbers");
        }
        String dimension = call.args().get(0);
        return dimension + " " + x + " " + y + " " + z + ": "
                + server.blockAt(dimension, x, y, z).orElse("not loaded (or no such dimension)");
    }

    private static String online(ServerControl server) {
        List<OnlinePlayer> players = server.players();
        return players.size() + " online" + (players.isEmpty() ? ""
                : ": " + players.stream().map(OnlinePlayer::name).collect(Collectors.joining(", ")));
    }

    private String deaths() {
        return deaths.isEmpty() ? "no deaths yet" : "deaths: " + deaths;
    }

    /** "minecraft:overworld at 12 64 -3, on minecraft:grass_block" */
    private static String where(ServerControl server, OnlinePlayer p) {
        int x = (int) Math.floor(p.x()), y = (int) Math.floor(p.y()), z = (int) Math.floor(p.z());
        String below = server.blockAt(p.dimension(), x, y - 1, z).orElse("an unloaded block");
        return String.format(Locale.ROOT, "%s at %d %d %d, on %s", p.dimension(), x, y, z, below);
    }
}
