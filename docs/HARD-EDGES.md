# The hard edges: custom rendering and deep engine hooks

**The critique (2026-10-13):**
- **Custom rendering and shaders.** Mods that hook rendering pipelines directly need manual rewrites when the rendering engine changes.
- **Deep engine Mixins.** Mods that inject bytecode into core logic (physics, lighting, chunk management) can crash when the code they target no longer exists in a newer version.

**Both points are correct.** No platform can make arbitrary bytecode patches, or GPU code written for one renderer, survive a rewrite of the code they patch. That includes Fabric, Forge and NeoForge as well as Conductor. Conductor's answer has three parts:
1. **Shrink.** Fewer mods should need such code at all.
2. **Move.** When the engine changes, the rewrite happens once, inside Conductor, instead of once per mod.
3. **Contain.** A broken hook switches a feature off instead of crashing the game.

## 1. What already shows this works (Verified, with live evidence in this repository)

| Case | What changed (1.21.4 → 1.21.11 unless noted) | What the modules had to change |
|---|---|---|
| **Overlays** (boxes, lines, tracers) | The world renderer itself. 1.21.4 draws in immediate mode (`Tesselator`, `BufferUploader`, `CoreShaders`). 1.21.11 uses `RenderPipeline` and `RenderType` built from private APIs. Conductor's two `VersionLineDrawer`s are about 70 lines each. | **Nothing.** The same `block-highlighter` jar drew on both versions ([live-run-highlighter.md](live-run-highlighter.md), [live-run-no-ostinato.md](live-run-no-ostinato.md)). |
| **Block render layers** (see-through glass and doors) | The layer type: `RenderType` became `ChunkSectionLayer`. | Nothing: modules say `RenderLayer.CUTOUT` ([live-run-content.md](live-run-content.md) §3). |
| **Mob renderers** | Renderer and model classes moved packages; renderers became render-state based. | Nothing: modules say `like(MobArchetype.ZOMBIE)` plus a texture (§5 there). |
| **Fully custom mobs: 1.16.1, 1.21.4, 1.21.11, 26.3** | Five years of entity and model APIs: 1.16.1 builds `ModelPart` boxes by hand and draws per entity; 1.21.x builds a part tree from a `LayerDefinition` and draws from a render state; entity types, attributes, projectiles and registries changed constructors and packages. | **Nothing.** The same jar's Ruby Golem (Bedrock geometry and animations, goals, a behaviour with slams, fireballs and summons, a boss bar) fought the same fight on all four ([live-run-custom-mobs.md](live-run-custom-mobs.md)). Model, Molang and animation code are one shared copy; only the entity and model classes are per version. |
| **Deep engine replacements next to Conductor** | Sodium replaces the chunk renderer; Lithium rewrites game logic. | Nothing: Conductor's hooks, content and overlays worked next to both (§4 there). |
| **Shaders next to Conductor (1.21.4, 1.21.11, 26.3)** | Iris (with Sodium) replaces the world's shaders with a shader pack's, and the pack decides each block's material from its own list of block names. | Nothing: the same jars drew blocks, mobs and overlays under the same pack on all three versions. Conductor itself gained one optional Iris hook, so module glass, leaves and panes get the pack's materials ([live-run-shaders.md](live-run-shaders.md)). |
| **A whole new renderer: 1.21.11 → 26.3** | 26.3 extracts the level into a frame before drawing it, and draws debug lines as gizmos. It picks block render layers from texture pixels; `RenderType` registration for blocks is gone. Conductor's 26.3 render hook moved to `LevelExtractor`, its `VersionLineDrawer` emits gizmos, and its render-layer class became a no-op. | **Nothing.** The same `block-highlighter` and `ruby-content` jars drew overlays, see-through glass, doors and mobs on 26.3 ([live-run-26.3.md](live-run-26.3.md)). |
| **A failing hook** | `Hooks.renderWorld` catches a renderer failure and switches overlays off; all Conductor Mixins are `required: false`. | The game keeps running. This is the platform rule "Conductor never crashes the game". |

The pattern: **a module states what it wants, and Conductor's per-version code decides how.** When the engine changes, that per-version code is the only thing that moves.

## 2. Custom rendering: a ladder, from most to least portable

1. **Data first.** Block, item and entity models, textures, item model definitions, and core shaders and post-effects in resource packs are JSON and GLSL files that the game itself loads and migrates. A content module ships them as data. The `mc_<version>` overlay covers format changes.
2. **Contract drawing primitives.** A module asks Conductor to draw; each Minecraft version has its own small drawer.
   - **Today:** lines, boxes, tracers, render layers, archetype mob renderers, and custom mob models with animations (Bedrock geometry and animation JSON, drawn by Conductor's per-version model class: [CUSTOM-MOBS.md](CUSTOM-MOBS.md)).
   - **Next, in order of how often client mods need them** (from the Meteor survey):
     - filled boxes and text labels in the world;
     - a 2D HUD (text, item icons, bars);
     - entity glow and outline;
     - block models beyond vanilla's JSON (entity models given as JSON are done, above).
3. **Native render modules.** Shader packs and engine replacements (Iris, Sodium) stay version-specific mods that run *next to* Conductor ([CONTENT-MODS.md](CONTENT-MODS.md) §6). Conductor's duty there is compatibility, tested live: Sodium and Lithium on 1.21.x, Iris with a shader pack on 1.21.4, 1.21.11 and 26.3 ([live-run-shaders.md](live-run-shaders.md)).

## 3. Deep engine hooks: shrink, move, contain

1. **Measure first: a Mixin census.**
   - `tools/convert` already reads every Mixin of a mod and the Minecraft classes it targets.
   - Run over a large sample of real mods, it counts which game code mods hook most.
   - Those targets become Conductor hook points, in order of how many mods need them. Fabric API grew the same way. First results: §4.
2. **Conductor-owned hook points.**
   - `conductor.runtime.Hooks` is the start: tick, chat, block changes, chunks, deaths, world rendering, registry freeze.
   - A module subscribes to a hook point (a topic or capability) instead of injecting into Minecraft.
   - Conductor rewrites the Mixin behind it when Minecraft changes; the module doesn't.
3. **Capabilities with meaning, not patches.** For common deep needs, the contract states the effect, and the runtime decides where to patch:
   - movement behind the MOVEMENT lease;
   - a block's light level (already declarative);
   - worldgen through data.
4. **Contain what remains (planned).**
   - Native modules will declare the Minecraft version they were built for, and Conductor will refuse one built for another version with a clear message.
   - Conductor will load a native module's Mixins as non-required and check every injection point.
   - When one is missing, Conductor will switch off *that module*, with a report naming the target, instead of crashing.
5. **Cheaper per-version work.** Mojang ships the 26.x line unobfuscated (Verified on 26.3: no `client_mappings`, and Conductor's 26.3 build uses Mojang's names directly). That makes "reference once per version" cheaper: no mapping step. It does not stop code structure changing, which is why steps 1–4 matter.

**What Conductor will not promise:**
- A mod that rewrites the lighting engine's internals needs rewriting when Mojang rewrites lighting.
- Conductor makes that the *only* part of the mod that needs rewriting: its content, settings, commands and other behaviour stay portable modules.
- The converter's report tells the author up front which parts those are.

## 4. Mixin census, first results

**Sample:** the 60 most-downloaded Fabric mods for 1.21.4 on Modrinth.
- 214 mods in all, counting nested jars.
- 141 of them have Mixins, targeting 893 different Minecraft classes.
- Full table: [evidence/mixin-census-1.21.4.md](evidence/mixin-census-1.21.4.md).
- Run with `tools/convert/conductor_convert.py --census --tiny <intermediary> --mojmap <mappings> <jars...>`. Status: Verified for this sample.

**What it shows:**
1. **The most-hooked classes are the ones Conductor already hooks.**
   - Mods target `Minecraft` (rank 1, 50 mods), `ClientPacketListener` (2, 28 mods) and `LevelRenderer` (7, 19 mods) most.
   - Conductor already has hook points there: client tick, chat and packets, world rendering.
   - Content hooks cover `PackRepository` (rank 50), the registries and creative tabs.
2. **The next hook points, by count:**
   - **HUD and screens:** `Gui` 16, `DebugScreenOverlay` 15, `AbstractContainerScreen` 14, `Screen` 13, `GuiGraphics` 12.
   - **Input:** `MouseHandler` 15, `KeyboardHandler` 14, `KeyMapping` 12.
   - **Server lifecycle and players:** `MinecraftServer` 21, `PlayerList` 19, `ServerPlayer` 15.
   - **Frame rendering:** `GameRenderer` 24.
   - **Entities:** `Entity` 20, `LivingEntity` 15. These are mostly events: damage, ticking, attributes.

   Most of these Mixins exist to observe or add something (draw on the HUD, read a key, react when a player joins). They don't change how the engine works. That is exactly what a contract hook point can replace.
3. **Deep engine targets are fewer than the critique implies.**
   - Chunk management ranks lower: `ChunkMap` 10th (17 mods), `LevelChunk` 24th (12), `ServerChunkCache` 48th (7). Most of those are the performance mods (C2ME, Lithium, ModernFix, FerriteCore) plus Fabric API's chunk events.
   - **No lighting class appears in the top 60.**
   - Engine replacements stay native modules (§3.4). They are a handful of well-maintained mods, not the long tail.

**Conclusion.** Most Mixins in popular mods hook the same 20–30 classes, for reasons a hook point can serve. Conductor's next hook points follow this ranking: HUD drawing, key input, then server lifecycle and player join. Each one built moves those Mixins out of mods and into Conductor's per-version code.
