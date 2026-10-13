# Live run: Conductor with no other mod, and with Ostinato as an add-on (2026-10-13)

**Question (project owner):** "Why does Conductor need Ostinato? It shouldn't have to rely on anything else."

**Answer:** it no longer does. Conductor's runtime needs only Minecraft. Its game hooks are now its own Mixins (client tick, outgoing chat, block, chunk, chat and death packets, world rendering). Ostinato is an optional *engine layer*, found at start-up through `META-INF/services/conductor.runtime.EngineLayerProvider`. When it is installed, Conductor adds travel and combat. When it isn't, Conductor logs `Conductor engine: none` and everything else works.

**Setup:**
- A production Fabric launch (`scripts/prod-run.sh`: KnotClient, game remapped to intermediary at start-up) joins a local vanilla server.
- `mods/` holds **only the Conductor mod**. No Ostinato, no Fabric API.
- Modules: `example-player-actions` and `example-block-highlighter` (`selftest=true`), plain jars, **the same files on both versions**.
- Server commands on join: give a sword and shield, apply Speed, summon a NoAI zombie two blocks away, place three diamond ores, send a `tellraw`, and replace one ore after 10 s.

Version label: the jars were built from this commit's code while the version was still 0.5.0. The version was then raised to 0.6.0, with no code change, so it is not confused with the released 0.5.0, which needed Ostinato.

**Evidence:**
- `docs/evidence/module-runs/no-ostinato-<version>.log.gz` (client) and `no-ostinato-server-<version>.log.gz`;
- screenshots: `docs/evidence/screenshots/no-ostinato-<version>.png`.

## 1. No Ostinato installed

| Check | 1.21.11 | 1.21.4 |
|---|---|---|
| Fabric loads Conductor's Mixins; game starts and joins | yes | yes |
| `Conductor engine: none (no movement engine installed; ...)` | logged | logged |
| `discover()` | player, world, actions, events, overlays, content. **No navigation or combat.** | same |
| `PLAYER_STATUS`, `PLAYER_MOTION`, `ACTIVE_EFFECTS`, `INVENTORY_SLOTS`, `SELECTED_SLOT`, `NEARBY_ENTITIES` | all observed (health 20, Speed amplifier 1, sword and shield, zombie at 2.0) | same |
| `blockAt` below / 10,000 blocks away; `findBlocks(bedrock)` | grass_block / **not loaded** / 3 positions | same |
| Leases HOTBAR, LOOK, INTERACT, CHAT | GRANTED | GRANTED |
| `selectHotbar(3)`, then `look` toward the zombie | **CONFIRMED** next tick (slot 3, yaw −90.0) | same |
| `sendChat` / second one in the same tick | server log `<Dev> Conductor player-actions module: hello` / `RATE_LIMITED` | same |
| `attack(zombie)` | health 20.0 → 19.63, **HIT CONFIRMED** | 18.0 → 12.10, **HIT CONFIRMED** |
| `attack(-1)`; `look` after releasing LOOK | `TARGET_UNAVAILABLE`; `LEASE_REQUIRED` | same |
| `Topics.CHAT` (received chat, Conductor's packet hook) | system lines and `tellraw` received | same |
| `Topics.BLOCK_CHANGES` (packet hook) | 3 × diamond_ore, then the replacement with stone | same |
| Chat commands (`+highlight`, `+set ... radius 12`, `+set ... radius 99`, `+settings`, `+modules`) | all handled; radius set to 12; 99 refused | same |
| Commands kept from the server (outgoing-chat hook) | server log contains **no** `+` line | same |
| Overlays (Conductor's render hook) | boxes and tracers drawn (screenshot) | same (screenshot) |

**Test-setup note (1.21.4):** the first 1.21.4 attempt did not start. The launch script put two ASM versions on the classpath: Minecraft 1.21.4's library list includes asm 9.6, next to the loader's 9.9. `scripts/prod_classpath.py` now lets the loader's copy win, as the launcher does. That failed attempt left a server-command helper waiting, and it fired again in the rerun. This is why that run shows two swords, two shields, two zombies and the `tellraw` twice. The cause is the test setup, not Conductor.

## 2. Ostinato installed as an add-on (1.21.11)

`mods/` holds the released Ostinato 1.21.0 module edition (`ostinato-mc1.21.11-unoptimized-fabric-1.21.0-module.jar`) and this Conductor build.

| Check | Result |
|---|---|
| `Conductor engine: Ostinato` | logged; the engine layer attached |
| `discover()` | the core capabilities **plus** navigation (`requestTravel, cancel, operationStatus`), navigation.secondary-backend (UNAVAILABLE: no Tungsten), combat |
| `example-walk-and-stop`: travel 20 blocks, cancel | EXECUTING → CANCELLING → **stop confirmed after 23 ticks; bot is AVAILABLE** |
| Ostinato's module (`baritone.conductor.OstinatoModule`), released 0.5.0 build, unchanged | registered; "Ostinato's movement commands now take Conductor's MOVEMENT lease" |
| `example-engine-commands` tour (`selftest=true`) | the player's `#goto ~40 ~ ~` runs under the lease → the SAFETY module gets **PENDING**, and Ostinato is stopped ("took over") → MOVEMENT **granted after 28 ticks, once the stop was proven** → `engine.run(goto ~-20 ~ ~)` accepted → the player's `#goto ~5 ~ ~` **refused** ("movement is held by example-engine-commands …") → `engine.stop`, proven after 4 ticks → `mine diamond_ore` without MOVEMENT: `LEASE_REQUIRED` → `version` accepted (not a task) → tour done |

Evidence: `docs/evidence/module-runs/with-ostinato-walk-and-stop-1.21.11.log.gz`, `with-ostinato-engine-commands-1.21.11.log.gz`. The existing Ostinato module edition needed no rebuild, because it uses only the contract.

## 3. What this does not show

- Combat with Ostinato was not re-run after the split. It goes through the same engine layer as travel, and its unit tests pass on both versions.
- Ostinato as an add-on was run on 1.21.11 only. The 1.21.4 engine layer is unit-tested against Ostinato's 1.21.4 classes.
- Other loaders (Quilt, Forge, NeoForge) were not run. The hooks are plain Mixins, but each loader still needs a boot shim.
