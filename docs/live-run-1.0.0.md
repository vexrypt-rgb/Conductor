# Live run: Conductor on Minecraft 1.0.0 (2026-10-13)

**Request (project owner):** "I think it would be pretty cool if you were able to get Minecraft 1.0.0 to work as a conductor version".

**Question this answers:** do the same module jars that run on 26.3, 1.21.x and 1.16.1 also run on Minecraft 1.0.0, from November 2011? That version has no command framework, no official names, numeric block and item ids, and separate client and server programs.

**Answer:** yes, for behaviour modules and server modules (Verified, details below).
- **Behaviour modules:** `example-player-actions` and `example-block-highlighter` ran on a 1.0.0 client joined to a 1.0.0 dedicated server, using the same jar files (same SHA-256) the build produces for every version.
- **Server modules:** `example-server-greeter` ran on the 1.0.0 dedicated server. It handled joins, leaves, deaths, chat and `/` commands from players and the console, and sent replies and broadcasts.
- **Content modules:** not supported. 1.0 has no registries that blocks, items or mobs could be added to, so Conductor logs a content module as unsupported and leaves it out. No content module was installed in these runs, so this path is not shown live (Unknown).
- **Not shown (Unknown):** `attack` and `useItem`. No hostile mob came within reach in a 60 s night run, and 1.0 has no `/summon` to place one.

## Setup

- **Launch:** Fabric Loader 0.19.5 on Java 21, through Ornithe's 1.0.0 support (Calamus intermediary, generation 2). The client runs `KnotClient` and the dedicated server runs `KnotServer`. The client uses Legacy Fabric's LWJGL 2.9.4 build, in headless Xvfb at 1280×720.
- **`mods/`, on both client and server:** one jar, `conductor-mod-1.0.0-0.6.0-mod.jar`, built from this commit. There is no Fabric API (none exists for 1.0) and no other mod.
- **Joining:** `-Dconductor.connect=localhost:25565` makes the 1.0 client join the server at start-up. This is what `--quickPlayMultiplayer` does on newer versions; 1.0 has no such option.
- **World:** fresh, with seed `conductor`, offline mode and view distance 4.

**Runs:**

| Run | Modules | Console | Evidence |
|---|---|---|---|
| server modules | server: `example-server-greeter-0.6.0`, `server-module-check` (harness: `/time` clash and a module that throws); client: a harness module that types chat lines and `/` commands | `deop`, `greet`, `whereami`, `online`, `blockat`, then `op Dev`, `deaths`, `kick Dev` | `mc10-server-modules-server.log.gz`, `mc10-server-modules-client.log.gz` |
| player actions | client: `example-player-actions-0.6.0`, `example-block-highlighter-0.6.0`; monsters on, night | `time set 14000` | `mc10-player-actions-client.log.gz` |
| block highlighter | client: `example-block-highlighter-0.6.0` with `selftest=true`, `block=minecraft:spruce_log` | `time set 6000`, `say hello from the console` | `mc10-block-highlighter-client.log.gz` |

All evidence is in `docs/evidence/module-runs/`. Three more logs back the findings below:
- `mc10-block-highlighter-oak-dropped-events-client.log.gz`: the `oak_log` run and the dropped events.
- `mc10-find-probe-client.log.gz`: the probe module's block reads.
- `mc10-region-updates-debug-client.log.gz`: a run with a temporary log line for every region packet.
- **Module jar hashes (SHA-256):**
  - `example-player-actions-0.6.0`: `2a9880c7…`
  - `example-block-highlighter-0.6.0`: `73421953…`
  - `example-server-greeter-0.6.0`: `3aa8d72d…`

  These are the jars `./gradlew build` produced for this commit.
- **Which build each run used:**
  - The server-modules and player-actions runs used the build *before* the last change, which lowered the client's region-update limit (see "What the runs found").
  - The block-highlighter run used the final build.
  - That change affects only how many block events a large region packet produces.

## How the 1.0.0 build differs

- **Names.**
  - Mojang published no names for 1.0, so Conductor is compiled against Ornithe's Feather names and shipped in Calamus intermediary names. This is the 1.0 equivalent of Fabric's intermediary.
  - `build.gradle` declares 1.0.0 as an `ancient` target. `gradle/runtime.gradle` and `gradle/fabric-module.gradle` add the Ornithe and Legacy Fabric repositories.
- **Two jars.**
  - 1.0's client and server are separate programs, and their classes have different names.
  - The server hooks (`conductor-mod/1.0.0/src/server`, `runtime/1.0.0/src/server`) are their own source set, compiled against the server jar and remapped with the server's names.
  - `modJar` merges the client-remapped jar with those server classes into one mod.
  - Fabric loads `conductor-server.mixins.json` only on a server.
- **Logging.**
  - 1.0 ships no log4j, so `ConductorLog` writes to stdout and stderr there.
  - On every other version it still uses log4j's `conductor` logger, unchanged.
- **Numeric ids.**
  - `LegacyIds` turns 1.0's block, item, effect, entity and dimension numbers into today's ids. For example, `35:14` becomes `minecraft:red_wool` and `17:1` becomes `minecraft:spruce_log`. A number with no modern name becomes `minecraft:legacy_<n>`.
  - `LegacyIds` also rewrites `give <player> <item id> [count]` into 1.0's numeric form when a server module runs it. For example, the greeter's `give Dev minecraft:bread 3` became `give Dev 297 3`.
  - 8 unit tests cover these mappings (`runtime/1.0.0/src/test/java/conductor/runtime/LegacyIdsTest.java`).
- **Players.** 1.0 has no player UUIDs, so a player is known by name and by the offline id derived from it. That is the id an offline-mode server gives on newer versions.
- **Hooks (client):**
  - `MinecraftMixin`: tick and joining a server.
  - `LocalClientPlayerEntityMixin`: outgoing chat. `+` lines are kept from the server.
  - `ClientNetworkHandlerMixin`: block updates, region updates, chunks loaded and forgotten, chat, and death. 1.0 has no death packet; a death is health dropping to 0.
  - `GameRendererMixin`: overlays, drawn after the world and before the held item.
- **Hooks (server):**
  - `MinecraftServerMixin`: tick and stop.
  - `ServerPlayNetworkHandlerMixin`: a player's `/` commands and chat.
  - `CommandHandlerMixin`: console commands.
  - 1.0 has no command framework, so a module command is answered before vanilla sees the line, and anything else goes to vanilla.
  - Joins, leaves and deaths come from the per-tick player list, as on the other versions.

## Results: server modules (Verified)

| Check | 1.0.0 | Same as newer versions? |
|---|---|---|
| Modules found and started | `started on Minecraft 1.0.0, dedicated server` | yes |
| A module command named like a vanilla one (`/time`) | `/time is already a command; left out` | yes |
| A module that throws in `joined` | turned off, stack trace logged, server keeps running | yes |
| Join | `Dev joined in minecraft:overworld at -63 65 246, on minecraft:air` (the player spawns 1.6 blocks up and falls) | yes |
| `runCommand("give Dev minecraft:bread 3")` | vanilla: `Giving Dev some 297` (bread) | yes, translated |
| Console `greet`, `whereami`, `online`, `blockat 0 0 0` | `Hello, CONSOLE!` / `Only a player can use /whereami` / `1 online: Dev` / not loaded | yes |
| Player `/greet`, `/greet Dev`, `/greet Nobody` | `Hello, Dev!` / `Dev says hello!` + `Said hello to Dev` / red `Nobody is not online` | yes |
| Player `/whereami`, `/online` | `You are in minecraft:overworld at -63 64 246, on minecraft:grass_block` / `1 online: Dev` | yes |
| Operator-only `/blockat` as a non-operator | `You do not have permission to use /blockat` | differs: newer versions hide the command (vanilla's "Unknown command") |
| `/blockat` as operator: 0 0 0, 100000 64 0, nether, bad arguments | not loaded / not loaded / not loaded / usage | yes |
| `/crashy` (turned off module) | the "turned off" reply | yes |
| Chat lines | `chat from Dev: hello from the client`, `ping` → `pong (tick 265)` | yes |
| Death (`/kill`) | broadcast `Dev died in minecraft:overworld at -63 64 246, on minecraft:grass_block (death 1)`; `/deaths` → `deaths: {Dev=1}` | yes |
| Leave (`kick Dev`) and stop | `Dev left; 0 online`; `stopping; deaths: {Dev=1}` | yes |

## Results: client modules (Verified)

| Check | 1.0.0 | Same as newer versions? |
|---|---|---|
| Fabric loads Conductor; mixins apply; game joins | yes | yes |
| `Conductor engine: none (...)`; both modules registered | logged | yes |
| `discover()` | player, world (blockAt, findBlocks), actions (selectHotbar, look, attack, useItem, sendChat, showMessage), events, overlays, content | yes |
| `PLAYER_STATUS` | health 20, food 20, saturation 5, air 300, on ground, survival | yes (1.0's values are whole numbers) |
| `PLAYER_MOTION`, `SELECTED_SLOT`, `ACTIVE_EFFECTS` | feet at y = 64.0 (1.0 keeps y at eye level; Conductor reports the feet) / 0 / none | yes |
| `NEARBY_ENTITIES` | item, creeper, zombies, cows at 26–31 blocks, with today's ids | yes |
| `blockAt` below / 10,000 blocks away | grass_block / **not loaded** | yes |
| `findBlocks` | spruce_log: 25, then 43 positions as chunks arrived; bedrock within 8: none (it is about 60 blocks down) | yes |
| Leases HOTBAR, LOOK, INTERACT, CHAT | GRANTED | yes |
| `look`, `selectHotbar(3)` | accepted; slot 3 **CONFIRMED** next tick | yes |
| `sendChat` / a second one in the same tick | chat came back as `<Dev> Conductor player-actions module: hello` / `RATE_LIMITED` | yes |
| `look` after releasing LOOK | `LEASE_REQUIRED` | yes |
| `Topics.CHAT` | `[Server] hello from the console` and player chat | yes |
| Chat commands (`+highlight`, `+set ... radius 12`, `+set ... radius 99`, `+settings`, `+modules`) | all handled; radius set to 12 and saved; 99 refused | yes |
| `showMessage` | the module's replies appear in 1.0's chat (screenshot) | yes |
| Overlays | boxes around the spruce trunks, drawn through leaves, with tracers from the crosshair (screenshot) | yes |
| `attack`, `useItem` | not exercised: no hostile mob within reach after 60 s | **Unknown** |

**Screenshots (not committed; described here).**
- **8 s after joining, highlighter run:** 1.0's daylight forest next to water, seen from the player.
  - Cyan wireframe boxes surround the spruce trunks, through their leaves, and cyan tracer lines run from the crosshair to each highlighted block.
  - The chat shows `[Server] hello from the console` in pink, then `[example-block-highlighter-0.6.0] 43 x minecraft:spruce_log within 8 blocks` and `example-block-highlighter-0.6.0 radius = 12`.
  - The hotbar is empty, and 1.0's "Press 'E' to open your inventory" hint is shown.
- **A first run asked for `oak_log` and reported `0 x minecraft:oak_log`.** The trees around the spawn were spruce. A probe module read `spruce_leaves` above the player and found grass, dirt and stone through `findBlocks`, so the search worked and simply had nothing to find.

## What the runs found and fixed

1. **Console colour codes.** The 1.0 console prints `§` as `?`, so replies to the console are sent without colour codes. Players still get the colours.
2. **`give` with an item id.** 1.0's `give` only takes numbers, so `runCommand` translates known item ids (`LegacyIds.command`). An id 1.0 does not have (for example netherite) is passed on unchanged, and vanilla says it is unknown.
3. **Long lines.** 1.0 clients disconnect on a chat line over 119 characters, so module replies and broadcasts are split at newlines and then every 119 characters (`LegacyEvents.chatLines`).
4. **Region updates flooding the event queue (Verified, fixed).**
   - When 10 or more blocks change in a chunk, a 1.0 server sends the box around them.
   - One fresh-world join sent 288 such boxes. 272 of them were at most 4096 blocks, and those were reported block by block: 170,134 block events.
   - One run filled Conductor's event queue (`dropped 58901 game events`). Nothing crashed, because the queue drops events by design.
   - Boxes over 256 blocks are now reported as their chunks reloaded, as boxes over 4096 already were. The block-highlighter run on the final build logged no dropped events.

## Test-setup notes

- **Harness-only modules:** `server-module-check` and a chat-script module (it types fixed chat lines and `/` commands, and `/kill`s the player at 46 s). They are not part of Conductor.
  - The chat script and the highlighter's self-test both need the CHAT lease, so they ran in separate runs.
- **Console commands 1.0 lacks:** the 1.0 console has no `kill`, so the player ran `/kill`; any 1.0 player may kill themselves.
- **Noise in a headless environment:** the client logs a JInput `NullPointerException` ("Cannot read the array length because "a" is null"), ALSA errors, and a failed download from Mojang's old resource server. All are vanilla and appear without Conductor.
