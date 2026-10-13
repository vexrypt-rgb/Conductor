# Server modules

A **server module** is code that runs where the game's server runs: on a dedicated server, or in the world a single-player game hosts for itself. It reacts to:
- server ticks;
- players joining, leaving and dying;
- chat;
- its own `/` commands.

It can also:
- message players;
- run console commands;
- read blocks.

The module is a plain jar written against `conductor.contract` only. The **same jar** runs on Minecraft 1.16.1, 1.21.4, 1.21.11 and 26.3. This was verified live on all four, on a dedicated server with a real client joining, and in single-player worlds on 1.21.4, 1.21.11 and 26.3 ([live-run-server-modules.md](live-run-server-modules.md)).

Server modules sit beside the other kinds of module:

| Kind | Runs | Service file | Guide |
|---|---|---|---|
| Behaviour module (`ConductorModule`) | on the client, for its player | `META-INF/services/conductor.contract.ConductorModule` | [TUTORIAL.md](TUTORIAL.md) |
| Content module (`ContentModule`) | on both sides: items, blocks, mobs, data | `META-INF/services/conductor.contract.ContentModule` | [CONTENT-MODS.md](CONTENT-MODS.md) |
| **Server module (`ServerModule`)** | on the server | `META-INF/services/conductor.contract.ServerModule` | this page |

One jar may hold several kinds. Each kind is loaded with its own class loader, so a jar's client module and its server module do not share static fields. Pass data between them through the game, not through statics.

## 1. A whole module

From [`examples/server-greeter`](../examples/server-greeter/src/main/java/conductor/example/greeter/ServerGreeter.java), shortened:

```java
public final class ServerGreeter implements ServerModule {

    private final Map<String, Integer> deaths = new TreeMap<>();

    @Override
    public List<ServerCommand> commands() {
        return List.of(
                ServerCommand.of("greet", "/greet [player]: say hello", this::greet),
                ServerCommand.operator("blockat", "/blockat <dimension> <x> <y> <z>", this::blockAt));
    }

    @Override
    public void joined(ServerControl server, OnlinePlayer p) {
        server.tell(p.name(), "Welcome, " + p.name() + "! This server runs Minecraft " + server.minecraftVersion() + ".");
        server.broadcast(p.name() + " joined; " + server.players().size() + " online");
        server.runCommand("give " + p.name() + " minecraft:bread 3");
    }

    @Override
    public void chat(ServerControl server, OnlinePlayer p, String message) {
        if (message.trim().equalsIgnoreCase("ping")) {
            server.broadcast("pong (tick " + server.tick() + ")");
        }
    }

    @Override
    public void died(ServerControl server, OnlinePlayer p) {
        server.broadcast(p.name() + " died (death " + deaths.merge(p.name(), 1, Integer::sum) + ")");
    }

    private String greet(ServerControl server, ServerCommand.Call call) {
        if (call.args().isEmpty()) {
            return "Hello, " + call.sender() + "!";
        }
        if (!server.tell(call.args().get(0), call.sender() + " says hello!")) {
            throw ServerCommand.fail(call.args().get(0) + " is not online");
        }
        return "Said hello to " + call.args().get(0);
    }
}
```

`META-INF/services/conductor.contract.ServerModule` holds one line: `conductor.example.greeter.ServerGreeter`. The build file needs only `compileOnly project(':contract')`, or the contract jar.

## 2. Events

Every method has a default that does nothing, so a module overrides only what it uses. All of them run **on the server thread**, at the end of a server tick, in this order:

| Method | When |
|---|---|
| `started(server)` | the first tick of each server: a dedicated server once, a single-player game each time a world is opened |
| `joined(server, player)` | a player is on the list who was not last tick |
| `chat(server, player, message)` | a player's chat message from this tick (not commands) |
| `died(server, player)` | a player's health went from above 0 to 0 |
| `left(server, player)` | a player from last tick is gone (with their last known state) |
| `tick(server)` | every tick, after the events above |
| `stopping(server)` | the server is stopping; players still on are not sent `left` |

Joins, leaves and deaths come from comparing the player list each tick, so they work the same way on every version. Respawning is not a join. Chat is collected when the message is sent and delivered in the next event round on the server thread.

## 3. What a module can do: `ServerControl`

| Method | Does |
|---|---|
| `minecraftVersion()`, `dedicated()`, `tick()` | which game this is, and the server's tick count |
| `players()`, `player(name)` | online players as `OnlinePlayer` records: name, UUID, dimension, x/y/z, health, spectator. `player` ignores case |
| `tell(player, message)` | a system message to one player; false if they are not online |
| `broadcast(message)` | a system message to everyone, also written to the server log |
| `runCommand(command)` | runs a command as the server console, with full permission. A leading `/` is allowed. Each one is logged (`Conductor server module <id> runs /<command>`); its output goes to the server log |
| `blockAt(dimension, x, y, z)` | the block id there (`minecraft:stone`), or empty if the dimension does not exist or the chunk is not loaded. It never loads a chunk |
| `log(message)` | a line in the server log, prefixed `[<module id>]` |

Messages are plain text. Vanilla commands through `runCommand` reach everything else (effects, items, teleports, titles, scoreboards), with the command syntax of the Minecraft version that is running.

## 4. Commands

`commands()` is read once, when modules are found. Each `ServerCommand` has:
- **a name**: 1–32 characters from `a-z 0-9 _ -`;
- **a help line**: logged when the command is added;
- **whether only operators may use it**: `ServerCommand.operator(...)` means permission level 2 (the level of `/gamemode`) and the console;
- **a handler**.

**Calls.** `/name` calls the handler with no arguments. Anything after the name is split on spaces into `call.args()`. `call.sender()` is the player's name, or `Server` for the console. `call.player()` is the player, empty for the console and command blocks.

**Answers.**
- The handler returns the reply, or null for none.
- To answer with an error (shown in red), throw `ServerCommand.fail("message")`. That is not a module failure.
- Any other exception turns the module off (§5). The sender is told `/<name> failed; see the server log`.

**Name clashes.** A name vanilla or another mod already has is left out, with a warning. So is a name an earlier module took.

**When commands are added.** At server start, and again on every `/reload`. A player who may not use a command does not see it, and gets vanilla's "Unknown or incomplete command".

## 5. Safety

- **Anything a module throws** is caught and logged, with the module id and the event. The module is turned off until the game restarts; its commands then answer `/<name> is off: its module (<id>) failed earlier`. The server and the other modules keep running.
- **Every Conductor hook** catches everything. If Conductor's own server-module code failed in a tick, the modules would be turned off until that server stops, logged once. The game would keep running.
- **Threads.** Everything runs on the server thread, so a module needs no locks for its own state. A module that blocks (sleeps, waits on the network) holds up the server tick. Start a thread of your own for slow work, and hand the result back to the next `tick`.

## 6. Install

Put the jar in `conductor/modules/` on the server, next to the Conductor mod for that server's version.

**Clients do not need Conductor.** Commands use only vanilla Brigadier parts (a literal and a greedy string), and messages are plain system messages. Checked live on 1.16.1 and 1.21.11, with a client that had no mods (Fabric Loader only) on a server running both modules:
- it joined, and stayed connected until it was kicked;
- it got the welcome, the `/greet` tell, and the join and death broadcasts;
- the server's `joined`, `died`, `left` and `stopping` events all fired;
- the commands, run as that player from the console (`execute as Dev run whereami`), answered correctly.

That client did not type the commands itself, so a mod-free client's command list was not checked.

**Single-player.** The jar may also go in a client's `conductor/modules/`. Then it runs in that player's single-player worlds, and in worlds they open to LAN. Checked live in single-player on 1.21.4, 1.21.11 and 26.3. 1.16.1 was not checked, because the test harness opens a world by quick play, which 1.16.1 does not have. Opening to LAN was not tried on any version.

## 7. How each version runs it

| | 1.16.1 | 1.21.4 | 1.21.11 | 26.3 |
|---|---|---|---|---|
| Ticks and stopping | Mixin on `MinecraftServer.tickServer` (end) and `stopServer` (start) | same | same | same |
| Commands | Mixin on the `Commands` constructor; Brigadier `literal` + greedy `args` | same | same | same |
| Chat | Mixin in `ServerGamePacketListenerImpl.handleChat`, before `PlayerList.broadcastMessage` | Mixin on `ServerGamePacketListenerImpl.broadcastChatMessage` (signed chat) | same | same |
| Operator check | `hasPermission(2)` | `hasPermission(2)` | `permissions().hasPermission(COMMANDS_GAMEMASTER)` | same as 1.21.11 |
| Messages | `TextComponent`, `sendMessage(..., NIL_UUID)` | `Component.literal`, `sendSystemMessage` | same | same |
| Live-verified: dedicated server + client | yes | yes | yes | yes |
| Live-verified: client without Conductor | yes | not run | yes | not run |
| Live-verified: single-player | not run | yes | yes | yes |

The rest (events, failures, commands, the module API) is one shared copy in `runtime/common` (`ServerModules`).

## 8. Limits (today)

- **Events:** only those listed above. No block break or place, no item use, no entity events, no advancements.
- **Chat** can be read but not changed or cancelled.
- **Messages** are plain text: no colours, clickable text or titles, except through `runCommand` (`tellraw`, `title`).
- **Arguments** are plain words: no tab completion for them, and no typed arguments (players, positions).
- **Blocks** can be read but not set, except through `runCommand` (`setblock`, `fill`).
- **No saved state:** a module keeps what it needs in its own files.
