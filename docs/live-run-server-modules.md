# Live run: server modules on 1.16.1, 1.21.4, 1.21.11 and 26.3 (2026-10-13)

**Modules:** two plain jars, **the same files on all four versions**:
- [`examples/server-greeter`](../examples/server-greeter) (`example-server-greeter-0.6.0.jar`, SHA-256 `3aa8d72d…de3f7`). It welcomes players and gives them bread with a console command, answers `ping`, announces deaths, and adds `/greet`, `/whereami`, `/online`, `/deaths` and the operator-only `/blockat`.
- [`scripts/server-module-check`](../scripts/server-module-check) (`server-module-check.jar`, SHA-256 `01ff0c14…5102`), a harness that is not an example. It holds two modules:
  - `Crashy`, a server module that throws on purpose in `joined`. It also asks for `/crashy` and for `/time`, a vanilla name.
  - `ChatScript`, a client module that types a timed script of chat lines and commands as the player.

**Setup** (`scripts/prod-run.sh`):
- Production launches with Fabric Loader 0.19.5 (KnotServer and KnotClient), with the Conductor mod for that version on both sides. No Fabric API and no other mods.
- Both jars are in the server's `conductor/modules`. The client has the same folder, for `ChatScript`.
- Each run starts with a fresh flat world.

The server console runs this after the player joins (`SERVER_ON_JOIN`):

```
deop Dev; wait 2 s; greet; whereami; online; blockat minecraft:overworld 0 0 0;
wait 26 s; op Dev; wait 16 s; kill Dev; wait 12 s; deaths; wait 6 s; kick Dev
```

The client types, in seconds after joining:

| s | line |
|---|---|
| 5, 7 | `hello from the client`, `ping` |
| 9, 11, 13 | `/greet`, `/greet Dev`, `/greet Nobody` |
| 15, 17 | `/whereami`, `/online` |
| 19 | `/blockat minecraft:overworld 0 0 0` (not an operator yet) |
| 21 | `/crashy` |
| 36–42 | as operator: `/blockat` at 0 0 0, at 100000 64 0 (far away), in `minecraft:nether` (no such dimension id), and `/blockat minecraft:overworld x` (bad usage) |
| 54, 56 | `/deaths` (after the death), `bye` |

## Dedicated server + client

| Check (from the logs) | 1.16.1 | 1.21.4 | 1.21.11 | 26.3 |
|---|---|---|---|---|
| `Conductor server modules found: [example-server-greeter-0.6.0, server-module-check]` | yes | yes | yes | yes |
| `/time is already a command; left out` (warning; the vanilla command stays) | yes | yes | yes | yes |
| `started on Minecraft <version>, dedicated server` | yes | yes | yes | yes |
| Join: the client gets the welcome and `Dev joined; 1 online`; the server logs the join place and the block under the player | yes | yes | yes | yes |
| `runCommand`: `runs /give Dev minecraft:bread 3`, and vanilla's `Gave 3 [Bread] to Dev` | yes | yes | yes | yes |
| `Crashy` throws in `joined`: logged with its stack trace, turned off; the greeter goes on | yes | yes | yes | yes |
| From the console: `greet` answers `Hello, Server!`; `whereami` fails with `Only a player can use /whereami` | yes | yes | yes | yes |
| Chat event (`chat from Dev: hello from the client`), and `pong (tick N)` in the client's chat | yes | yes | yes | yes |
| `/greet` → `Hello, Dev!`; `/greet Dev` → `Dev says hello!` + `Said hello to Dev`; `/greet Nobody` → red `Nobody is not online` | yes | yes | yes | yes |
| `/whereami`, `/online` | yes | yes | yes | yes |
| `/blockat` as a non-operator: vanilla's `Unknown or incomplete command` | yes | yes | yes | yes |
| `/crashy` → `/crashy is off: its module (server-module-check) failed earlier; see the server log` | yes | yes | yes | yes |
| `/blockat` 0 0 0 as an operator | `not loaded` (see below) | `minecraft:air` | `minecraft:air` | `minecraft:air` |
| `/blockat` far away, and in `minecraft:nether` → `not loaded (or no such dimension)`; bad usage → `Usage: …` | yes | yes | yes | yes |
| `/kill Dev`: the `died` broadcast with place and block; then `/deaths` → `deaths: {Dev=1}` | yes | yes | yes | yes |
| `chat from Dev: bye` | yes | yes | yes | yes |
| After the kick: `Dev left; 0 online`; at shutdown `stopping; deaths: {Dev=1}` | yes | yes | yes | yes |
| Conductor warnings or errors other than the two expected ones (`/time`, `Crashy`) | none | none | none | none |

**1.16.1 and `/blockat` 0 0 0.** The player spawned at −115 4 −232, about 15 chunks from chunk 0,0. On this run that chunk was not loaded, and `blockAt` never loads a chunk, so `not loaded` is the right answer. In the client-without-Conductor run on 1.16.1 below, the spawn was at −101 4 −134. Chunk 0,0 was loaded there, and the same command answered `minecraft:bedrock` (a flat world's bottom layer on 1.16.1).

**Screenshots** (taken; not committed, since they show Minecraft's own HUD and terrain):
- 26.3 at 20 s: the chat shows `Dev says hello!`, `Said hello to Dev`, `Nobody is not online` in red, `You are in minecraft:overworld at 2 -60 4, on minecraft:grass_block`, `1 online: Dev`, and vanilla's red `Unknown or incomplete command` for the non-operator `/blockat`. The hotbar holds 3 bread.
- 1.16.1 at 50 s: the death screen (`Dev fell out of the world`; that is what 1.16.1's `/kill` says). Behind it is the module's line `Dev died in minecraft:overworld at -115 4 -232, on minecraft:grass_block (death 1)`.

## Single-player (the client's own server)

`SINGLEPLAYER=<world>` in `prod-run.sh`. No dedicated server is started. The client opens a copy of a world by quick play, with cheats on, so the player is an operator. Both module jars are in the client's `conductor/modules`, and the same `ChatScript` runs.

| Check | 1.21.4 | 1.21.11 | 26.3 |
|---|---|---|---|
| `Conductor server modules started on a single-player server`; `started on Minecraft <version>, single-player` | yes | yes | yes |
| `/time` left out; `Crashy` off after throwing in `joined`; the greeter goes on | yes | yes | yes |
| Welcome, join broadcast, `give` | yes | yes | yes |
| Chat event and `pong` | yes | yes | yes |
| `/greet` (all three), `/whereami`, `/online`, `/crashy is off …`, `/deaths` → `no deaths yet` | yes | yes | yes |
| `/blockat`: allowed at once (cheats on); `minecraft:air` at 0 0 0; far away and `minecraft:nether` not loaded; usage | yes | yes | yes |
| `tick 1200: 1 online: Dev` (the greeter's minute log) | yes | yes | yes |

Not shown in single-player:
- `stopping` and `left`: the harness kills the client at the end, so the integrated server never shuts down cleanly. Both events were shown on the dedicated runs.
- `died`: not run here; it was shown on the dedicated runs.
- 1.16.1 single-player was not run, because quick play does not exist on 1.16.1.

## A client without Conductor

The dedicated server is set up as above. The client has no mods at all: Fabric Loader only, with no Conductor and no `ChatScript`. So the console runs the commands as the player: `execute as Dev run greet`, `… greet Dev`, `… whereami`, `… online`, `… blockat minecraft:overworld 0 0 0`, `… crashy`. Then come `kill Dev`, `execute as Dev run deaths` and `kick Dev`.

| Check | 1.16.1 | 1.21.11 |
|---|---|---|
| The client joins, and stays connected until the kick (the command tree with the modules' commands is accepted) | yes | yes |
| The client's chat: the welcome, `Dev joined; 1 online`, `Dev says hello!` (a `tell`), the `died` broadcast | yes | yes |
| Replies, run as Dev: `Hello, Dev!`, `Said hello to Dev`, `You are in …` (the handler got Dev as the player), `1 online: Dev`, `/crashy is off …`, `deaths: {Dev=1}` | yes | yes |
| `/blockat` 0 0 0 | `minecraft:bedrock` | `minecraft:air` |
| Server events: `joined`, `died`, `left`, `stopping` | yes | yes |

The replies went to the server log, not the player's chat: `execute as` changes the entity but keeps the console as the receiver. Not checked: a client without Conductor typing the commands itself, including its tab list of them.

## Evidence

All logs are in `docs/evidence/module-runs/`. The scratch path is shown as `<scratch>`.
- Dedicated runs: `server-modules-server-<version>.log.gz` and `server-modules-client-<version>.log.gz`, for 1.16.1, 1.21.4, 1.21.11 and 26.3.
- Single-player: `server-modules-singleplayer-<version>.log.gz`, for 1.21.4, 1.21.11 and 26.3. The integrated server writes into the client's log.
- Client without Conductor: `server-modules-modfree-{server,client}-<version>.log.gz`, for 1.16.1 and 1.21.11.

Other lines in the logs, all from earlier runs too and none from server modules:
- Mojang authentication 401 and Realms errors: the client is offline;
- `Failed to open OpenAL device`: no sound card;
- the `BlockMaterialMapping` class-not-found warning (1.21.4, 1.21.11, 26.3): Conductor's optional Iris mixin, with Iris not installed;
- 26.3's `Requested post effect does not exist: minecraft:end_of_frame`;
- netty `StacklessClosedChannelException` at the kick on 1.21.11.
