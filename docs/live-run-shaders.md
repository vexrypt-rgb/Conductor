# Live run: Conductor next to Iris and a shader pack, 1.21.4, 1.21.11 and 26.3 (2026-10-13)

**Request (project owner):** "How can we get mods like iris to work properly across each version".

**What this answers:** does Conductor, with its overlay and content modules, work next to Iris (with Sodium) and a real shader pack? What does Conductor have to do so module content looks right under shaders?

**Answer, in short:**
- Iris stays an ordinary, per-version mod. Iris's authors already publish a build for each Minecraft version (table below). Conductor's job is to stay compatible with it, as with Sodium ([CONTENT-MODS.md](CONTENT-MODS.md) §6).
- The shader pack is portable: the **same zip file** ran on all three versions.
- With no changes, Conductor ran next to Iris + Sodium + the pack on 1.21.4, 1.21.11 and 26.3:
  - no crash and no Mixin warnings about Conductor;
  - content blocks, items and mobs drawn under the pack;
  - overlays drawn.
  
  (Verified, results below.)
- One gap, found by reading the pack, is now closed in Conductor:
  - **The gap.** Shader packs list blocks by name in `block.properties`, so they know nothing of module blocks. Module glass, leaves and panes would get no material (refraction, waving) from the pack.
  - **The fix.** Conductor now gives each module block of shape `GLASS`, `LEAVES` or `PANE` the material the pack gave vanilla glass, oak leaves or glass panes.
  - **How it works.** It is an optional hook into Iris, and it does nothing when Iris is absent.
  - **Status.** It ran on all three versions. The visual difference could not be measured on this software renderer (Unknown, see "Not checked").

## Iris builds per Minecraft version

From Modrinth's API on 2026-10-13. Fabric builds; Iris needs Sodium of the same line.

| Minecraft | Latest Iris | Needs Sodium | Tested here |
|---|---|---|---|
| 1.16.1 | none: Iris's 1.16 builds are for 1.16.5 (21 versions listed), none for 1.16.1 | — | no |
| 1.21.4 | 1.8.8 | 0.6.x (0.6.13 used) | yes |
| 1.21.11 | 1.10.8 | 0.8.x (0.8.15-beta.1 used) | yes |
| 26.3 | 1.11.7 | 0.9.x (0.9.2 used) | yes |

So on 1.16.1 there is no Iris to be compatible with. A 1.16.1 player who wants shaders would need OptiFine, which was not tested.

## Setup

- **Launch:** production Fabric launch (`scripts/prod-run.sh`).
  - Loader 0.18.5 (1.21.4) or 0.19.5 (1.21.11, 26.3), on Java 25.
  - Mesa llvmpipe software rendering under Xvfb; EGL on 26.3, see [live-run-26.3.md](live-run-26.3.md).
- **Client `mods/`:** the Conductor mod for the version (built from this commit), plus Sodium and Iris.
  - Sodium bundles parts of Fabric API, so the clients loaded 17 mods (1.21.4) or 20 (1.21.11, 26.3).
  - All jars came from Modrinth, checked against Modrinth's SHA-512.
- **Shader pack:** Complementary Reimagined r5.9.3, the same file on all three versions.
  - It is put in `shaderpacks/` and selected in `config/iris.properties` (`shaderPack=…`, `enableShaders=true`).
  - Both reach the game folder through `prod-run.sh`'s new `GAME_FILES` option, a folder copied into the game folder.
  - The client log confirms each run: `Using shaderpack: ComplementaryReimagined_r5.9.3.zip`.
- **Modules:** the client runs `example-block-highlighter` (ruby blocks, radius 12, tracers) and `example-ruby-content` (SHA-256 `14ac3259…18acd`, the jar of the 26.3 run).
- **Server:** a Fabric dedicated server with Conductor and the ruby module. It runs these commands:
  - place the ruby blocks (block, pillar, stairs, slab, wall, glass, door) and two floating ruby blocks;
  - summon a NoAI ruby zombie (with fire resistance) and a ruby pig.
- **Third-party files:** the downloaded jars, the pack and screenshots of the pack's output were used only locally. Nothing from them is committed.
- **Evidence:** the logs only, `docs/evidence/module-runs/shaders-iris-{client,server}-<version>.log.gz`. They had the container's proxy line removed and the scratch path replaced with `<scratch>`.

## Results

| Check | 1.21.4 | 1.21.11 | 26.3 |
|---|---|---|---|
| Loads and joins; no Mixin warning or error naming Conductor | yes | yes | yes |
| Iris loads the pack and compiles its programs (shadow pass included) | yes | yes | yes |
| Content registered both sides (`7 block(s), 9 item(s) and 3 mob(s)`), `renderers for 3 mob(s)` | yes | yes | yes |
| Blocks under the pack (screenshots, kept local) | all textured; the pack's shadows fall on and from them; glass and the door window see-through | same | same |
| Mobs under the pack (screenshots) | ruby zombie textured, scaled up (server on `easy`, see note 2) | same | same |
| Overlays (`block-highlighter` boxes and tracers) | drawn, in full colour, on top of the shaded image | same | same |
| Hotbar item icons | drawn | same | same |
| Conductor's Iris hook (`shader pack materials for 1 module block state(s)`) | logged | logged | logged |

The screenshots look the same on all three versions. The pack's sky, shadows and grass shading are on, which shows the pack was active.

The first runs were done before the Iris hook existed. Apart from the hook's log line, they gave the same results on 1.21.4 and 1.21.11. Their logs are not committed.

**Notes:**
1. **The black border in every screenshot** is the test harness: the earlier Sodium-only run has it too ([live-run-content.md](live-run-content.md) §4).
2. **The zombie on 1.21.4.**
   - The test servers run on peaceful difficulty. In two 1.21.4 runs the ruby zombie was gone by the 60-second screenshot.
   - The earlier mob run hit the same thing ([live-run-content.md](live-run-content.md) §5).
   - With `difficulty easy` the server confirmed `CHECK zombie still here` 30 seconds after summoning, and the screenshot shows it, burning in daylight.
   - On 1.21.11 and 26.3 it was still there on peaceful at 90 seconds. Why that differs between versions was not looked into.
3. **Log lines from the pack, not Conductor:**
   - `Unknown variable: BIOME_SULFUR_CAVES` (all three) and `endFlashIntensity` (1.21.4): the pack names features its Iris version does not have;
   - shader compiler warnings such as `'lmCoord' used uninitialized`;
   - `Error while parsing the block ID map entry … stone_slab has no property … variant`.

## The fix: module blocks in the pack's material map

**The gap (Verified by reading the pack).**
- Complementary's `shaders/block.properties` has 508 lines of block names, for example `block.32008=glass` and `block.10009=… oak_leaves:waterlogged=false …`. It uses no block tags.
- Iris can read tags there (it has a `TagEntry` class), but this pack doesn't use them.
- So a module's glass got the default material: drawn as glass-shaped, but without the pack's glass treatment.

**What Conductor does now:**
- **`ContentInstaller.shaderLookalikes()` (runtime, shared by 1.21.x and 26.3).** It keeps, for each module block of shape `GLASS`, `LEAVES` or `PANE`, the vanilla lookalike: glass, oak leaves, or glass pane.
  - Other shapes are left alone. A ruby block given stone's material, for example, could pick up stone-specific effects that do not fit.
  - The module declares nothing new: the shape it already declares decides.
- **`IrisBlockIdsMixin` (conductor-mod, shared by 1.21.4, 1.21.11 and 26.3).** A `@Pseudo` Mixin on Iris's `BlockMaterialMapping.createBlockStateIdMap`, named by string:
  - Without Iris there is no target, and nothing happens.
  - The injection is optional (`require = 0`), so a future Iris that renames the method costs the materials, not the game.
- **`ShaderMaterials.addContentBlocks`.** It copies each lookalike's material id to the module block's states, matching properties by name (`waterlogged`, `distance`, `persistent`, …). It runs once each time Iris builds the map from a pack.
  - A state the pack lists by name keeps the pack's id.
  - Any failure is logged and Iris's own map is returned unchanged.

**Why this point (Verified with `javap -c`).** In all three Iris builds tested (1.8.8, 1.10.8, 1.11.7):
- `IrisRenderingPipeline` passes the map from `createBlockStateIdMap` straight to `WorldRenderingSettings.setBlockStateIds`;
- the method's name and parameters are the same.

## Not checked (Unknown)

- **Whether the glass looks different with the material.** The before/after crops of the glass differ less than the grass control does. On this software renderer, frame-to-frame noise hides any effect. A real GPU and a pack with a strong glass effect would show it.
- **Leaves and panes:** the ruby module has none, so their mapping ran in code but not on a live block.
- **Overlay shadows:**
  - No shadow of the overlay lines is visible in the screenshots.
  - Whether Iris's shadow pass could ever reach Conductor's render hook was not looked into. On 1.21.x the hook is at the end of `LevelRenderer.renderLevel`; on 26.3 it is in `LevelExtractor.extract`.
- **Other packs** (BSL, SEUS, …), **Iris on NeoForge**, **performance**, the **dimension under shaders**, and the **behaviour module** (`player-actions`) next to Iris.
- **1.16.1:** no Iris build exists (above).

## How to run it again

```
GAME_FILES=<folder with config/iris.properties and shaderpacks/<pack>.zip> \
MODS=<conductor-mod jar>:<sodium jar>:<iris jar> SERVER_MODS=<conductor-mod jar> ... scripts/prod-run.sh <workdir>
```

Iris writes `config/iris.properties` itself the first time it runs. The two keys above select and enable a pack without opening its menu.
