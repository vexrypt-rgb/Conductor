# Live run: Conductor on Minecraft 26.2 (2026-10-13)

**Question this answers:** does the 26.3 build work on 26.2, the release before it, on Java 25, with the same module jars?

**Answer:** yes, for both kinds of module, in one run each (Verified). The module jars are the same files as in the 1.16.5 and 1.16.1 runs; only Conductor's own 26.2 layer was built for this version.

| Module jar | SHA-256 |
|---|---|
| `example-player-actions-0.6.0.jar` | `3e008b6176867f06ee1c66e4dfc71044b538fa7cd4290470e1a26a92da34a8da` |
| `example-block-highlighter-0.6.0.jar` | `8346603c8fc0e84b771c8ebf160474dd37f41a5f5325eefee70aceaed517b590` |
| `example-ruby-content-0.6.0.jar` | `d2fab63928f921a8a57bdadd5fb61a82a422666a955bb831fcdfc0c15e3fb98e` |
| `example-server-greeter-0.6.0.jar` | `8595294d5340309ce52612787dc20bc5796491e6e37031d1160bec0ab0a428b8` |
| `conductor-mod-26.2-0.6.0.jar` (built from this branch) | `b01f20a891713e631a34ec9b5e62d8c919a953027d52bf3bb3a68092c0985e66` |

## Setup

- **Launch:** a production Fabric launch (`scripts/prod-run.sh`, KnotClient), Fabric Loader 0.19.5 on **Java 25**. 26.2 is unobfuscated (no intermediary; the loader runs the game as Mojang ships it).
- **Server:** vanilla 26.2 `server.jar` (SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a`, from Mojang's manifest).
- **Client rendering:** Mesa EGL is needed for 26.x's SDL window (`libegl1 libegl-mesa0`); `prod-run.sh` sets `SDL_VIDEO_FORCE_EGL` for 26.x.
- **No Ostinato and no Fabric API.**

## Run 1: behaviour modules (client and vanilla server)

- Modules: `example-player-actions` and `example-block-highlighter`, `selftest=true`.
- Server commands on join: sword and shield; Speed; a NoAI zombie with Fire Resistance; three diamond ores; a `tellraw`; after 10 s one ore becomes stone.

**Evidence:** `docs/evidence/module-runs/mc26.2-client.log.gz`, `mc26.2-server.log.gz`, `docs/evidence/screenshots/mc26.2.png`.

| Check | 26.2 result |
|---|---|
| Fabric loads Conductor; Mixins apply; game joins | yes |
| `Conductor engine: none`; both modules registered | logged |
| Player status, effects, slots, entities | health 20, Speed, sword and shield in slots 0 and 1, zombie at 2.0 blocks |
| `findBlocks(diamond_ore)` | 3 ores |
| Leases HOTBAR, LOOK, INTERACT, CHAT | GRANTED |
| `selectHotbar(3)` | CONFIRMED next tick |
| `sendChat`; a second one in the same tick | server `<Dev> Conductor player-actions module: hello`; second one RATE_LIMITED |
| `attack(zombie)` | health 20.0 → 19.63, HIT CONFIRMED |
| `attack(-1)`; `look` after releasing LOOK | TARGET_UNAVAILABLE; LEASE_REQUIRED |
| `Topics.BLOCK_CHANGES` | the ore replaced with stone is reported |
| Chat commands (`+highlight`, `+set … radius 12`, `+settings`, `+modules`) | all handled; radius 12 |
| Overlays (render hook, gizmo lines) | cyan boxes around the ores (screenshot) |

## Run 2: content module and server module (dedicated server and client)

- Dedicated Fabric server (`KnotServer`) on the vanilla 26.2 `server.jar`, with `conductor-mod`, `example-ruby-content` and `example-server-greeter`. The client has `conductor-mod` and `example-ruby-content`.
- Server commands on join: give the items; `setblock` each block kind and a door; summon the three mobs with NoAI; `recipe give`; `loot`; `datapack list`.

**Evidence:** `docs/evidence/module-runs/mc26.2-content-client.log.gz`, `mc26.2-content-server.log.gz`, `docs/evidence/screenshots/mc26.2-content.png`.

| Check | Result |
|---|---|
| Registration, both sides | `registered 7 block(s), 9 item(s) and 4 mob(s) from 1 module(s) [ruby]` |
| Data pack | `conductor/ruby (built-in)` enabled |
| Dimension | `ruby:ruby_world` loads and is saved with the server. Not checked: teleporting into it (the 26.3 run did) |
| Items | `Gave 5 [item.ruby.ruby]`, `Gave 1 [item.ruby.ruby_apple]` |
| Mobs | summoned; client `renderers for 4 mob(s)` |
| Recipes, loot | `Unlocked 1 recipe(s) for Dev`; `Dropped 1 [block.ruby.ruby_block]` |
| Server module | `example-server-greeter` ran its `/give` command (`Gave 3 [Bread] to Dev`) |
| Blocks (screenshot) | ruby blocks drawn with their textures |
| Warnings | `No data fixer registered for ruby:ruby_*` (4 lines, client and server). Vanilla logs this for every modded entity type; the 26.3 logs show it too. Harmless. |

## How the 26.2 build differs from 26.3

The shared code is the same. These calls differ in 26.2 (checked against the 26.2 jar, then compiled and run):

- **Attack swing:** 26.2 has no `ServerboundPunchPacket`. `LocalPlayer.swing(hand)` sends `ServerboundSwingPacket` itself, so the swing after an attack is that one call.
- **Velocity sync:** the flag is `Entity.hurtMarked` (the 26.3 code uses `syncVelocity`).
- **Piston reaction:** `PushReaction.DESTROY` (the 26.3 code uses `PushReaction.POPPED`).
- **View and suffocation:** `isViewBlocking` takes a three-argument predicate.
- **Chunk load packet:** `getX()`/`getZ()` (a getter class, not a record).

The render hook is unchanged: 26.2 has `LevelExtractor.extract` and `extractGizmos`, so `LevelExtractorMixin` and the gizmo line drawer work as on 26.3. All Mixin target methods were checked against the 26.2 jar.

## Not done on 26.2

- Teleporting into the ruby dimension (installed, not exercised).
- Travel and combat: no movement engine on 26.2 (as on 26.3).
- Iris and shader packs: the Iris class is absent (the `IrisBlockIdsMixin` warning is expected).
