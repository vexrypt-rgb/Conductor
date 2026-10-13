# Live run: Conductor on Minecraft 1.16.5

**Question this answers:** does the 1.16.1 port work on 1.16.5 (the last 1.16 release, pack format 6), with the same module jars?

**Answer:** yes, for both kinds of module, in one run each (Verified). The module jars were built once against the contract and not changed.

| Module jar | SHA-256 |
|---|---|
| `example-player-actions-0.6.0.jar` | `3e008b6176867f06ee1c66e4dfc71044b538fa7cd4290470e1a26a92da34a8da` |
| `example-block-highlighter-0.6.0.jar` | `8346603c8fc0e84b771c8ebf160474dd37f41a5f5325eefee70aceaed517b590` |
| `example-ruby-content-0.6.0.jar` | `d2fab63928f921a8a57bdadd5fb61a82a422666a955bb831fcdfc0c15e3fb98e` |
| `example-server-greeter-0.6.0.jar` | `8595294d5340309ce52612787dc20bc5796491e6e37031d1160bec0ab0a428b8` |
| `conductor-mod-1.16.5-0.6.0.jar` (built from this branch) | `019b5e7ea74088c9612d2533e026fb7db3b46ec86a05f72182e4e8ffc6bf92a0` |

## Setup

- **Launch:** a production Fabric launch (`scripts/prod-run.sh`, KnotClient, intermediary mappings), Fabric Loader 0.19.5 on Java 21, joining a local vanilla 1.16.5 server (`server.jar` SHA-1 `1b557e7b033b583cd9f66746b7a9ab1ec1673ced`, from Mojang's manifest). The harness joins through `scripts/legacy-join`, as for 1.16.1.
- **No Ostinato and no Fabric API.** `conductor-mod-1.16.5` is the only Conductor jar.

## Run 1: behaviour modules (client and vanilla server)

- Modules: `example-player-actions` and `example-block-highlighter` with `selftest=true` (`conductor/config/<module>.properties`).
- Server commands on join: give a sword and a shield; Speed; a NoAI zombie two blocks away with Fire Resistance; three diamond ores; a `tellraw`; after 10 s one ore becomes stone.

**Evidence:** `docs/evidence/module-runs/legacy-1.16.5-client.log.gz`, `legacy-1.16.5-server.log.gz`, `docs/evidence/screenshots/legacy-1.16.5.png`.

| Check | 1.16.5 result |
|---|---|
| Fabric loads Conductor; Mixins apply; game joins | yes; no Mixin errors in the log |
| `Conductor engine: none`; both modules registered | logged |
| `discover()` | player, world, actions, events, overlays |
| Player status, effects, slots, entities | health 20, Speed, sword and shield in slots 0 and 1, zombie at 2.0 blocks |
| `blockAt` below; `findBlocks(diamond_ore)` | grass_block; 3 ores |
| Leases HOTBAR, LOOK, INTERACT, CHAT | GRANTED |
| `selectHotbar(3)` | CONFIRMED next tick |
| `sendChat`; a second one in the same tick | server log `<Dev> Conductor player-actions module: hello`; second one RATE_LIMITED |
| `attack(zombie)` | health 20.0 → 19.45, HIT CONFIRMED |
| `attack(-1)`; `look` after releasing LOOK | TARGET_UNAVAILABLE; LEASE_REQUIRED |
| `Topics.BLOCK_CHANGES` (block update) | the ore replaced with stone is reported |
| Chat commands (`+highlight`, `+set … radius 12`, `+set … radius 99`, `+settings`, `+modules`) | all handled; radius 12; 99 refused |
| Overlays (render hook) | cyan boxes and tracers on the diamond ores in view (screenshot; the ore replaced with stone is not boxed) |

## Run 2: content module and server module (dedicated server and client)

- Dedicated Fabric server (`KnotServer`) on the vanilla 1.16.5 `server.jar`, with `conductor-mod`, `example-ruby-content` and `example-server-greeter`. The client has `conductor-mod` and `example-ruby-content`.
- Server commands on join: give the items; `setblock` each block kind and two door halves; summon the three mobs with NoAI; `recipe give`; `loot`; `datapack list`.

**Evidence:** `docs/evidence/module-runs/legacy-1.16.5-content-client.log.gz`, `legacy-1.16.5-content-server.log.gz`, `docs/evidence/screenshots/legacy-1.16.5-content.png`.

| Check | Result |
|---|---|
| Registration, both sides | `registered 7 block(s), 9 item(s) and 4 mob(s) from 1 module(s) [ruby]` |
| Data pack | `21 file(s) translated for Minecraft 1.16.5`; `datapack list` shows `conductor/ruby (built-in)` |
| Items | `Gave 5 [item.ruby.ruby]`, `Gave 1 [item.ruby.ruby_apple]`; the hotbar shows the items with icons |
| Mobs | `Summoned new entity.ruby.ruby_zombie` / `_spider` / `_pig`; client `renderers for 4 mob(s)` |
| Recipes | `Unlocked 1 recipes for Dev` for `ruby:ruby_block` |
| Loot tables | `Dropped 1 [block.ruby.ruby_block]` |
| Blocks (screenshot) | ruby blocks drawn with their textures |
| Server module | `example-server-greeter` ran its `/give` command (`Gave 3 [Bread] to Dev`) |
| Translation notes | server: `data/ruby/dimension/ruby_world.json: dimension files are not translated for Minecraft 1.16.5 yet; left out`; client: none |
| Model or texture warnings | none |

## How the 1.16.5 build differs from 1.16.1

- **Pack format 6** (1.16.2 moved resource and data packs to 6; 1.16.1 had 5). `ContentInstaller.PACK_FORMAT` is 6.
- **Pack loading:** `Pack.PackConstructor` is no longer generic in 1.16.5; `loadPacks` takes `Consumer<Pack>`. Both pack Mixins follow vanilla's new signature.
- **Chunk updates:** the section update packet is `ClientboundSectionBlocksUpdatePacket` and is read with `runUpdates` (1.16.1 used `ClientboundChunkBlocksUpdatePacket.getUpdates()`).
- **`BlockPlaceContext`** moved to `net.minecraft.world.item.context`.

## Not done on 1.16.5

- **Dimension files** (`data/<ns>/dimension`, `dimension_type`, `worldgen`): 1.16.5 can read them, but the translator does not convert the 1.21 format yet, so they are left out with a note. The ruby module's dimension is therefore not installed.
- **Travel and combat:** no movement engine on 1.16.5 (as on 1.16.1).
- **Shaders and engine replacements:** Iris has no 1.16 build.
