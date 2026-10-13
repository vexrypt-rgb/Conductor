# Live run: Conductor on Minecraft 1.16.1 (2026-10-13)

**Request (project owner):** "I also want you to make a 1.16.1 Conductor version to really test it's ability".

**Question this answers:** do module jars built for 1.21.x run unchanged on a Minecraft that is five years older, with only Conductor's own per-version layer in between?

**Answer:** yes, for behaviour modules. The same `example-player-actions` and `example-block-highlighter` jars that ran on 1.21.11 and 1.21.4 (same files, same SHA-256) ran on 1.16.1 and gave the same results. **Content modules are not installed on 1.16.1 yet.** That is the next step, covered at the end.

## Setup

- **Launch:** a production Fabric launch (`scripts/prod-run.sh`: KnotClient, game remapped to intermediary at start-up), Fabric Loader 0.19.5 on Java 21, joining a local vanilla 1.16.1 server.
- **`mods/`:**
  - `conductor-mod-1.16.1-0.6.0.jar`, built from this commit;
  - `conductor-test-join`, a harness-only helper (see "Test-setup notes").
  - No Ostinato and no Fabric API.
- **Modules:** `example-player-actions-0.5.0.jar` and `example-block-highlighter-0.5.0.jar` with `selftest=true`. These are the same files used in `live-run-no-ostinato.md`.
- **Server commands on join:**
  - give a sword and a shield, and apply Speed;
  - summon a NoAI zombie two blocks away, with Fire Resistance (see notes);
  - place three diamond ores and send a `tellraw`;
  - after 10 s, replace one ore with stone.

**Evidence:**
- `docs/evidence/module-runs/legacy-1.16.1-client.log.gz` and `legacy-1.16.1-server.log.gz`;
- `docs/evidence/screenshots/legacy-1.16.1.png`.

## How the 1.16.1 build differs

- **`build.gradle`:** declares 1.16.1 as a `legacy` target. It uses Mojang mappings only (there is no Parchment for 1.16.1) and has no Ostinato engine layer.
- **`runtime/common`:** compiled for every version. `runtime/modern` holds code that needs 1.21 APIs (content installation, the 1.21 player access).
- **`conductor-mod/common`:** the client-tick Mixin and the entrypoints. `conductor-mod/modern` holds the 1.21-only content Mixins.
- **Logging:** Conductor logs through log4j-api, which every Minecraft from 1.16.1 to 1.21.11 ships. 1.16.1 has no slf4j.
- **1.16.1's own classes:**
  - `MinecraftGameAccess`, `MinecraftPlayerAccess`: `Registry.*`, the `inventory` field, `yRot`/`xRot` fields, `LocalPlayer.chat`.
  - `VersionLineDrawer`: fixed-function GL lines, with no shaders in 1.16.
  - Mixins for chat, render and packets: 1.16.1 packet shapes; player chat arrives as `chat.type.text`.

## Results

| Check | 1.16.1 | Same as 1.21.x? |
|---|---|---|
| Fabric loads Conductor; Mixins apply; game joins | yes | yes |
| `Conductor engine: none (...)`; both modules registered | logged | yes |
| `discover()` | player, world, actions, events, overlays, content | yes |
| `PLAYER_STATUS`, `PLAYER_MOTION`, `ACTIVE_EFFECTS`, `INVENTORY_SLOTS`, `SELECTED_SLOT`, `NEARBY_ENTITIES` | health 20, Speed amplifier 1, sword and shield, zombie at 2.0 | yes |
| `blockAt` below / 10,000 blocks away; `findBlocks(bedrock)` | grass_block / **not loaded** / 3 positions | yes |
| Leases HOTBAR, LOOK, INTERACT, CHAT | GRANTED | yes |
| `selectHotbar(3)`, then `look` toward the zombie | **CONFIRMED** next tick (slot 3, yaw −90.0) | yes |
| `sendChat` / a second one in the same tick | server log `<Dev> Conductor player-actions module: hello` / `RATE_LIMITED` | yes |
| `attack(zombie)` | health 20.0 → 19.81, **HIT CONFIRMED** | yes |
| `attack(-1)`; `look` after releasing LOOK | `TARGET_UNAVAILABLE`; `LEASE_REQUIRED` | yes |
| `Topics.CHAT` (received chat) | system lines, `tellraw`, and `<Dev> Conductor player-actions module: hello` | yes, same text |
| `Topics.BLOCK_CHANGES` | 3 × diamond_ore, then the replacement with stone | yes |
| Chat commands (`+highlight`, `+set ... radius 12`, `+set ... radius 99`, `+settings`, `+modules`) | all handled; radius set to 12; 99 refused | yes |
| Commands kept from the server (outgoing-chat hook) | server log has **no** `+` line | yes |
| Overlays (render hook) | boxes and tracers drawn (screenshot) | yes |

**Hooks woven in (Verified).** The run used `-Dmixin.debug.export=true`, and each handler below has one call site in the exported class:
- `Minecraft`: tick.
- `LocalPlayer.chat`: outgoing chat.
- `LevelRenderer.renderLevel`: render.
- `ClientPacketListener`: block update, section update, chunk loaded, chunk forgotten, chat, death.

The death and chunk events are woven in, but no example module subscribes to them, so this run did not observe them firing (Unknown).

## Test-setup notes

1. **Vanilla 1.16.1 crashes when started with `--server`** (Verified twice, with no mods at all).
   - In 1.16.1, `--server` connects while the first resource reload is still running.
   - Chunks are then drawn before the block models are baked, and the game crashes with "Tesselating block in world" (a NullPointerException on a null `BakedModel` in `ModelBlockRenderer`).
   - Evidence: `docs/evidence/module-runs/legacy-1.16.1-vanilla-server-flag-crash.log.gz`.
   - Quick play, which 1.21 uses, does not exist before 1.20.
   - Fix: on 1.1x versions, `prod-run.sh` builds `scripts/legacy-join` and drops it into `mods/`. This harness-only mod waits for the loading overlay to appear and then go away, then opens the connect screen through reflection on intermediary names. It is not part of Conductor.
   - The 1.16.1 constructor shows the title screen just *before* it puts up the loading overlay. A first version of the helper that only checked "no overlay, title screen" joined too early and hit the same crash.
2. **Zombie burning in daylight.**
   - Two runs (one on 1.16.1, one on 1.21.4) reported `NO DAMAGE SEEN`. Both times the zombie had already lost health to sunlight before the attack.
   - The empty-hand hit (1 damage) seems to have landed inside the zombie's invulnerability window after fire damage.
   - With `effect give @e[type=minecraft:zombie] minecraft:fire_resistance 300 0 true`, the zombie stays at 20 health, and the same test gives **HIT CONFIRMED** on 1.16.1 (20.0 → 19.81) and 1.21.4 (20.0 → 19.63). Evidence: `no-ostinato-fire-resistant-zombie-1.21.4.log.gz`.
   - The invulnerability-window explanation fits both results but was not measured directly.
3. **Particles in screenshots.** Large blue translucent squares near the camera in some 1.16.1 screenshots are the player's own Speed-effect particles. A run with no mods shows them too.
4. **Expected warnings.** Missing sound files (sounds are not downloaded for these runs), no OpenAL device under Xvfb, and the LWJGL 3.2.2 "Unsupported JNI version" warning on Java 21 all appear with no mods too.

## Not done yet on 1.16.1

- **Content modules** (blocks, items, mobs). The 1.16.1 `ContentInstaller` is a placeholder that installs nothing. No content module has been run on 1.16.1 yet.
- Installing content needs 1.16.1's own code:
  - `Registry.register`, plus block states added to `Block.BLOCK_STATE_REGISTRY`;
  - creative tabs set through `Item.Properties`;
  - pack format 5 and the 1.16 `PackRepository`;
  - the 1.16 `DefaultAttributes` map and mob renderers.
