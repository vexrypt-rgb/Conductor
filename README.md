# Conductor
Version agnostic modular system for bot capabilities in Minecraft

Conductor is a version-agnostic module platform for Minecraft. It needs nothing but Minecraft and a boot shim: its own small set of game hooks (Mixins) gives modules the player, the world, actions, events and overlays. A movement engine is optional: when Ostinato (a Baritone fork) is installed, Conductor adds travel and combat on top, with the same evidence-based safety rules. Conductor began as the modularity layer for Ostinato and still never changes it.

## Status

| Stage | State | Document |
|---|---|---|
| 1. Audit | Complete (documentation only) | [docs/stage1-audit.md](docs/stage1-audit.md) |
| 2. Discovery + read-only observations | Complete; verified live | [docs/stage2-observations.md](docs/stage2-observations.md) |
| 3. Single-consumer navigation delegation | Complete; 54/54 tests pass; verified live | [docs/stage3-navigation.md](docs/stage3-navigation.md) |
| 4. Ownership, resource safety, interference classification | Complete; verified live on 1.21.11 and 1.21.4 | [docs/stage4-release.md](docs/stage4-release.md) |
| Module platform: many modules, leases with priorities, topics, services, management | Implemented; unit-verified | [docs/MODULE-PLATFORM.md](docs/MODULE-PLATFORM.md) |
| Version-agnostic features (content detection) | Implemented; verified live on both versions | [docs/TUTORIAL.md §12](docs/TUTORIAL.md#12-one-jar-every-version-optional-features) |
| 5. Combat delegation (Ostinato PvP/PvE) | Implemented; verified live on both versions | [docs/live-run-combat.md](docs/live-run-combat.md) |
| Player, world and actions (status, entities, inventory, blocks; hotbar, look, attack, use, chat) | Implemented; verified live on both versions | [docs/live-run-player-actions.md](docs/live-run-player-actions.md) |
| Overlays, game events, chat commands, local messages, saved settings | Implemented; verified live on both versions, with screenshots | [docs/live-run-highlighter.md](docs/live-run-highlighter.md) |
| Meteor Client survey: what a full client needs, and the roadmap | Survey done | [docs/survey-meteor-client.md](docs/survey-meteor-client.md) |
| **Content modules**: items, blocks, data packs (recipes, loot, tags, dimensions, worldgen) and assets, from one plain jar; Conductor on dedicated servers | Implemented; verified live on a Fabric dedicated server plus client, 1.21.11 and 1.21.4, same jar | [docs/CONTENT-MODS.md](docs/CONTENT-MODS.md), [docs/live-run-content.md](docs/live-run-content.md) |
| **Mod converter** (`tools/convert`): readiness report and content-module skeleton from Fabric/Quilt/Forge/NeoForge jars | v1; a real Fabric mod (Thaumon) converted and run on Conductor | [tools/convert/README.md](tools/convert/README.md) |
| **No other mod needed**: Conductor's own game hooks (Mixins); Ostinato is an optional engine layer | Done; verified in production Fabric launches on 1.21.11 and 1.21.4 with no Ostinato, and with Ostinato as an add-on on 1.21.11 | [docs/live-run-no-ostinato.md](docs/live-run-no-ostinato.md) |
| **Minecraft 1.16.1**: the same module jars on a five-year-older game | Behaviour modules and the ruby content module verified live in a production Fabric launch, client and dedicated server (same jars as 1.21.x); content files translated for 1.16.1 at load | [docs/live-run-1.16.1.md](docs/live-run-1.16.1.md) |
| **Ostinato as a module** (Ostinato branch `conductor-module`: its `#` commands take the movement lease; `EngineCommands` for every Ostinato feature) and the **1.21.11 release** | Released (Ostinato 1.21.0 + Conductor 0.5.0); verified in a production Fabric launch with the shipped jars | [docs/live-run-ostinato-module.md](docs/live-run-ostinato-module.md) |

**New here? Start with the [tutorial](docs/TUTORIAL.md)**: what Conductor does, every capability, and how to build a module. Worked examples:
- [`examples/walk-and-stop`](examples/walk-and-stop): travel.
- [`examples/agnostic-combat`](examples/agnostic-combat): one jar whose spear part switches itself off on 1.21.4.
- [`examples/player-actions`](examples/player-actions): reads and actions.
- [`examples/block-highlighter`](examples/block-highlighter): a block ESP with overlays, commands and settings.
- [`examples/ruby-content`](examples/ruby-content): a content module (items, a block, recipes, loot, a dimension).

**Limitations (today):**
- Travel and combat need a movement engine (Ostinato). Without one, `discover()` lists no navigation or combat, and requests are rejected with `NO_NAVIGATION` / `NO_COMBAT`. Everything else works without it.
- Travel and combat are the only engine actions. Mining, building and the rest are still deferred.
- No 2D HUD, keybinds, raw packets, motion writes, inventory moves or block placing yet. Next steps are in [the Meteor survey](docs/survey-meteor-client.md) §5.
- Modules are trusted code. Leases coordinate them but can't stop a module that bypasses Conductor.
- Boots on Fabric only, on Minecraft 1.21.11, 1.21.4 and 1.16.1 (1.16.1 with no movement engine; dimension files need 1.16.2+). Modules themselves don't depend on any loader. Conductor's hooks are plain Mixins, which every major loader runs.
- Stops take as long as Ostinato's engine takes (up to about 17 s observed).

Full list: [tutorial §10](docs/TUTORIAL.md#10-limits-you-should-know-about).

**Long-term goal:** become a unified, version-agnostic Minecraft modding platform. Write a mod once against the contract, then run it on every supported version next to every other mod: utility/anarchy and legit PvP modules alike. The principles, gaps and a staged path are in [docs/VISION-client-mod-host.md](docs/VISION-client-mod-host.md).

Deferred capability candidates (combat and others): [docs/deferred-candidates.md](docs/deferred-candidates.md)

## Layout

- `contract/`: the normative contract, the Java binding (`Conductor`) and the module interface (`ConductorModule`). Pure Java 21, no Minecraft dependency.
- `runtime/`: Conductor's runtime. It needs only Minecraft (no engine is on its compile classpath). Shared code is in `common/` (all versions) and `modern/` (1.21.x); `1.21.11/` and `1.21.4/` differ only in `MinecraftGameAccess`; `1.16.1/` also has its own player access, content installer and pack translator. Includes `Hooks` (the entry points Conductor's Mixins call), `ConductorRuntime` and `ConductorBootstrap`.
- `engine-ostinato/`: the optional Ostinato engine layer (travel, combat, engine signals). Bundled in the Conductor mod and loaded only when Ostinato is installed (`META-INF/services/conductor.runtime.EngineLayerProvider`).
- `conductor-mod/`: the Conductor mod (one per Minecraft version) that hosts every module. It holds Conductor's game hooks (Mixins for tick, chat, packets and world rendering) and a 40-line Fabric boot shim, its only loader-specific part ([docs/LOADER-INDEPENDENCE.md](docs/LOADER-INDEPENDENCE.md)).
- `examples/`: example modules (`ruby-content` is a content module). Each is a plain jar (not a Fabric mod) for every version, and goes in `<game dir>/conductor/modules/`.
- `live-harness/`: test-only mod for live runs, built for both versions.
- `tools/convert/`: the mod converter (Python, no dependencies).

## Live harness

`live-harness/` is a test-only Fabric mod. `scripts/live-run.sh <workdir>` starts a vanilla 1.21.11 server, then runs Ostinato's dev client under Xvfb with the harness, which records every observation plus player position and velocity per tick. `scripts/analyze-live.py <workdir>/harness.jsonl` summarises the cancel trials. See the script header for prerequisites.

## Build

```sh
# contract only (no Minecraft needed)
./gradlew :contract:test

# the runtime needs only Minecraft
./gradlew :runtime-1.21.11:test

# the Conductor mod bundles the optional Ostinato engine layer, which compiles against a local Ostinato checkout's
# classes (default ../Ostinato; -PostinatoDir_1_21_4=/path for 1.21.4). This is a build-time need only: the built
# mod runs with or without Ostinato installed.
(cd ../Ostinato && ./gradlew compileJava compileApiJava)
./gradlew test build
```
