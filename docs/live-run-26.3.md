# Live run: Conductor on Minecraft 26.3 (2026-10-13)

**Request (project owner):** "after 1.16.1, do a 26.3 version".

**Question this answers:** do the same module jars that ran on 1.16.1, 1.21.4 and 1.21.11 run unchanged on Minecraft 26.3, which Mojang ships unobfuscated, on Java 25, with a new renderer?

**Answer:** yes, for both kinds of module (Verified, one run each, results below).
- **Behaviour modules:** the same `example-player-actions` and `example-block-highlighter` jars gave the same results as on the other three versions.
- **Content modules:** the same `example-ruby-content` jar installs its blocks, items, mobs, recipes, loot and its dimension on a 26.3 Fabric dedicated server and client.

Only Conductor's own per-version layer changed. The modules were not rebuilt.

| Module jar | SHA-256 |
|---|---|
| `example-player-actions-0.5.0.jar` | `c4cd518a5b9579608729c286e468b899115c7687af81f5581b60d45f4c8c2cda` |
| `example-block-highlighter-0.5.0.jar` | `c91d3cde9fb61007ceae61ebe5075dfbc94c97fdc10ee5f70b472df287ae3721` |
| `example-ruby-content-0.6.0.jar` | `14ac3259be62f4cf61be77c2af0f6e7dafbaa6f4ca43bb3f8f3dee4264318acd` (the 1.16.1 and 1.21.x content runs used this file too) |

## Setup

- **Launch:** a production Fabric launch (`scripts/prod-run.sh`, KnotClient). Fabric Loader 0.19.5 on Java 25. 26.3 has no intermediary mappings, so the loader runs the game as Mojang ships it, with no remapping.
- **`mods/`:** `conductor-mod-26.3-0.6.0.jar`, built from this commit. No Ostinato and no Fabric API.
- **Behaviour run:**
  - joins a local vanilla 26.3 server;
  - same server commands as the 1.16.1 run: sword and shield, Speed, a NoAI fire-resistant zombie, three diamond ores and a `tellraw`, then one ore replaced with stone;
  - at the end, the player is turned toward the ores for the screenshot;
  - the block highlighter has `selftest=true`.
- **Content run:** a Fabric **dedicated** server (KnotServer) on the vanilla 26.3 `server.jar`, with Conductor and the ruby module on both sides. The commands are the same as the 1.16.1 content run, plus a teleport into the module's dimension.

**Evidence:**
- `docs/evidence/module-runs/mc26.3-client.log.gz` and `mc26.3-server.log.gz` (behaviour);
- `docs/evidence/module-runs/mc26.3-content-client.log.gz` and `mc26.3-content-server.log.gz` (content);
- `docs/evidence/screenshots/mc26.3.png` and `mc26.3-content.png`.

The logs had the container's proxy line removed and the scratch folder path replaced with `<scratch>`. Nothing else was changed.

## How the 26.3 build differs

- **`build.gradle`:** declares 26.3 as `noEngine`, `unobfuscated`, Java 25. The build uses Mojang names only: no intermediary and no Parchment. There is no Ostinato engine layer yet.
- **`runtime/26.3`:** the same shared sources as 1.21.x (`runtime/common` and `runtime/modern`), plus five small version classes:
  - `VersionBlocks`:
    - `PushReaction.POPPED` (1.21's `DESTROY`);
    - the new `isViewBlocking` shape;
    - leaves built as `TintedParticleLeavesBlock`;
    - block items registered the way vanilla's now-private `Items.registerBlock` does it.
  - `VersionPlayer`: the attack swing uses the held item's animation and sends its own punch packet, as vanilla's `startAttack` does. Client-only messages go through `Gui.hud`.
  - `VersionIds`, `VersionMobs`, `MinecraftGameAccess`: the same as 1.21.11.
- **`conductor-mod/26.3`:**
  - **Rendering.**
    - 26.3 extracts the level into a frame before drawing it.
    - Conductor's render hook moved from `LevelRenderer` to `LevelExtractor.extract`, just before vanilla collects its gizmos.
    - Overlay lines are drawn as vanilla gizmos (`Gizmos.line`, `setAlwaysOnTop` for lines seen through walls).
  - **Block render layers.** 26.3 picks each block's layer from its texture's pixels, so Conductor no longer sets one. A texture with fully transparent pixels draws cut out; one with part-transparent pixels draws translucent.
  - **Chunk packets:** `ChunkPos` and the chunk packet are records here, so `VersionChunks` reads `x()`/`z()`.
  - **The other Mixins** (registries, creative tabs, packs, attributes, entity renderers, tick, chat, packets) are the shared 1.21 ones. Their targets were checked with `javap` against the 26.3 jars.
- **Harness:**
  - `scripts/prod_classpath.py` leaves intermediary out for versions with no `client_mappings`.
  - `scripts/prod-run.sh` sets `SDL_VIDEO_FORCE_EGL=1` for 26.x clients (see "Test-setup notes").

## Results: behaviour modules

| Check | 26.3 | Same as 1.16.1 and 1.21.x? |
|---|---|---|
| Fabric loads Conductor; Mixins apply; game joins | yes; no Mixin warnings or errors in the log | yes |
| `Conductor engine: none (...)`; both modules registered | logged | yes |
| `discover()` | player, world, actions, events, overlays, content | yes |
| `PLAYER_STATUS`, `PLAYER_MOTION`, `ACTIVE_EFFECTS`, `INVENTORY_SLOTS`, `SELECTED_SLOT`, `NEARBY_ENTITIES` | health 20, Speed amplifier 1, sword and shield, slot 0, zombie at 2 | yes |
| `blockAt` below / 10,000 blocks east; `findBlocks(bedrock)` | grass_block / **not loaded** / 3 positions | yes |
| `findBlocks(diamond_ore)` | the 3 placed ores | yes |
| Leases HOTBAR, LOOK, INTERACT, CHAT | GRANTED | yes |
| `selectHotbar(3)`, then `look` toward the zombie | **CONFIRMED** (slot 3, yaw −90.0) | yes |
| `sendChat` / a second one in the same tick | server log `<Dev> Conductor player-actions module: hello` / `RATE_LIMITED` | yes |
| `attack(zombie)` | health 20.0 → 19.81, **HIT CONFIRMED** | yes |
| `attack(-1)`; `useItem(offhand)`; `look` after release | `TARGET_UNAVAILABLE`; accepted; `LEASE_REQUIRED` | yes |
| `Topics.CHAT` | server lines (26.3 wording, e.g. "Set minecraft:overworld to time marker minecraft:noon"), `tellraw`, the module's own message | yes |
| `Topics.BLOCK_CHANGES` | 3 × diamond_ore, then the replacement with stone | yes |
| Chat commands (`+highlight`, `+set ... radius 12`, `+set ... radius 99`, `+settings`, `+modules`) | all handled; radius 12 set; 99 refused | yes |
| Commands kept from the server | the server log has **no** `+` line | yes |
| Overlays | outline boxes and tracers drawn, including the box around the buried ore (screenshot) | yes |

A second, earlier run with the same jars gave the same results (attack 20.0 → 19.06). Its logs were overwritten, so it is not part of the evidence.

**Hooks woven in (Verified).** The client ran with `-Dmixin.debug.export=true`. `javap` of the exported classes shows these Conductor calls:
- `Minecraft`: `Hooks.clientTick`.
- `LevelExtractor`: `Hooks.renderWorld`.
- `ClientPacketListener`:
  - `blockChanged` (×2: block update and section update);
  - `chatReceived` (×2);
  - `chunkLoaded` (×2: loaded and forgotten, through `VersionChunks`);
  - `outgoingChat`;
  - `playerDied`.

As on 1.16.1, the death and chunk hooks are woven in, but no example module subscribes to them, so they were not seen firing (Unknown).

## Results: content modules

| Check | Result (server log unless noted) |
|---|---|
| Registration, both sides | `registered 7 block(s), 9 item(s) and 3 mob(s) from 1 module(s) [ruby]` |
| Data pack | `Found new data pack conductor/ruby, loading it automatically`; `datapack list`: `[vanilla (built-in)], [conductor/ruby (built-in)]` |
| Items and block items | `Gave 5 [item.ruby.ruby]`, `ruby_apple`, `ruby_door`, `ruby_glass`, `ruby_stairs`; the client shows the English names (`Ruby Zombie`), so the lang file loaded |
| Blocks | every `setblock` accepted |
| Blocks (screenshot) | block, pillar, stairs, slab, wall, glass, a closed door and two open doors, all textured. The glass is see-through: grass shows through it, so the pixel-based layer choice gave it a cut-out layer. |
| Mobs | `Summoned new entity.ruby.ruby_zombie` / `_spider` / `_pig`; ruby zombie health `30.0f`, attack damage `5.0`; ruby spider health `12.0f`; ruby pig speed `0.3` (all the declared values); `[Ruby Zombie] CHECK I am a ruby zombie` |
| Mob renderers (client) | `renderers for 3 mob(s)`; the screenshot shows all three textured, with the zombie scaled up and the spider scaled down |
| Recipes | `recipe give` for `ruby:ruby_block` and `ruby:ruby_from_block`: `Unlocked 1 recipe(s)` each |
| Loot tables | `Dropped 1 [block.ruby.ruby_block] from loot table ruby:blocks/ruby_block` (once per `loot ... mine` command, as on 1.16.1); `Dropped 1 [item.ruby.ruby] from loot table ruby:entities/ruby_pig` |
| Dimension | `execute in ruby:ruby_world run tp ...` worked, and `the ground here is ruby:ruby_block`. An earlier run with the same jar and commands also has a screenshot of the ruby floor. It is not committed; the server log line is the evidence. |
| Item models (client) | the hotbar shows all six given items with their icons |
| Client warnings about ruby models or textures | none |

**Not checked (Unknown):** survival mining with and without a pickaxe; crafting in a crafting table; eating the ruby apple; translucent (part-transparent) textures, since the module has none.

## Test-setup notes

1. **No window under Xvfb without EGL** (environment, not Conductor).
   - 26.3 opens its window with SDL3 and asks for an sRGB-capable OpenGL 3.3 core context.
   - This container's Xvfb GLX offers no sRGB-capable visual. A small probe counted 40 RGB8 configs and 0 with sRGB.
   - So the client cannot open its window ("Couldn't find matching GLX visual"). Vulkan had no driver either.
   - Fix: install Mesa's EGL (`libegl1 libegl-mesa0`) and set `SDL_VIDEO_FORCE_EGL=1`. The game then logs `Using graphics backend OpenGL, using drivers: 4.5 (Core Profile) Mesa 25.2.8`, rendering on llvmpipe.
   - `prod-run.sh` now sets the variable for 26.x.
2. **Gamerules are snake_case in 26.3** (`spawn_mobs`, `advance_time`). The run scripts use the new names.
3. **Expected log lines.** These appear with no mods, or in the same runs on 1.16.1 and 1.21.x:
   - `No data fixer registered for ruby:*` (vanilla logs this for any mod entity type; it is in the 1.21.11 mob runs too);
   - `No key layers in MapLike[{}]` (the flat-world settings in the test `server.properties`);
   - the authentication 401, Realms and OpenAL errors (offline test account, no sound device);
   - Java 25 warnings about LWJGL's and JNA's native access.

## Not done yet on 26.3

- **No Ostinato engine layer,** so no travel or combat. Ostinato has a 26.x branch; building the engine layer against it is the next step.
- **Other mods:** Sodium 0.9.2 with Iris 1.11.7 and a shader pack ran on 26.3 ([live-run-shaders.md](live-run-shaders.md)). Lithium was run only on 1.21.x (see `CONTENT-MODS.md`).
