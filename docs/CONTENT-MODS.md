# Hosting content mods: items, blocks, mobs, dimensions, structures

**Project owner (2026-10-13):** make sure Conductor can host every other type of mod: custom mobs, items, dimensions, structures and so on. Fabric, Forge, Quilt and NeoForge may be used as reference. Also plan tools that convert existing mods to Conductor.

**Basis:** [survey-content-apis.md](survey-content-apis.md), which compares how each loader does it, checked against the 1.21.4 and 1.21.11 game jars. The design below uses loaders as reference only. Conductor depends on none of them.

## 1. Two kinds of content

| Kind | Examples | Where it lives in the game | How Conductor hosts it |
|---|---|---|---|
| **Data content** | dimensions, dimension types, biomes, structures, structure sets, template pools, worldgen features, recipes, loot tables, tags, enchantments, damage types, mob variants, paintings, trims, jukebox songs | JSON (and NBT) loaded from data packs when a world or server loads | **Packs:** a module jar's `data/` and `assets/` folders become a pack the game loads, on client and server. No code. |
| **Code content** | items, blocks, block entities, entity types (mobs), particles, sounds, effects, menus, new *kinds* of feature or structure | built-in registries, filled from Java code at start-up and then **frozen** | **Declarations:** a module describes its content through the contract. Conductor registers it, per Minecraft version, in the window before the freeze. |

Most of what people call "a content mod" is both: a block is code (the block) plus data (its loot table, recipe and tags) plus assets (models, textures, names).

## 2. The facts the design rests on (from the survey, Verified)

1. **Built-in registries are frozen before any mod entrypoint runs.** Vanilla `Main` calls `Bootstrap.bootStrap()` → `BuiltInRegistries.bootStrap()` = `createContents(); freeze(); validate()` before the game or server object exists. After the freeze, even *constructing* a `Block` or `Item` throws.
2. **Fabric Loader doesn't change that; Fabric API does.** It redirects the bootstrap call to delay the freeze. Forge and NeoForge run their own `RegisterEvent` before it.
3. **`BuiltInRegistries.freeze()` is a private static method in both versions.** Content registered just before it is registered like vanilla's own, after vanilla's entries (so vanilla ids don't move).
4. **A block's states must be added to `Block.BLOCK_STATE_REGISTRY`** (vanilla does it in `Blocks`' static initialiser, before our content exists).
5. **Packs need no loader.** `PackRepository` takes `RepositorySource`s; a `Pack` can be built with its metadata in code (`new Pack(location, resources, metadata, selection)`), so a module jar needs no `pack.mcmeta` with a version-specific pack format. `required` packs are always selected.
6. **Built-in registry ids are not synced by vanilla.** Items, blocks and entities go over the network as numbers in registration order. Client and server must therefore register **the same content in the same order** (or run an id-sync handshake, as Fabric API, Forge and NeoForge do).
7. **The relevant APIs are the same in 1.21.4 and 1.21.11** (`Item.Properties.setId`, `BlockBehaviour.Properties.setId`, `Items.registerItem/registerBlock`, `Pack`, `PackLocationInfo`, `PackSelectionConfig`, `CreativeModeTab.buildContents`), except for the id class (`ResourceLocation` vs `Identifier`), which sits in one per-version class.

## 3. Design

### 3.1 The contract (version-agnostic, plain Java)

A **content module** implements `conductor.contract.ContentModule` and is listed in `META-INF/services/conductor.contract.ContentModule`, in the same kind of plain jar as other modules (one jar may hold both kinds).

```java
public final class RubyContent implements ContentModule {
    public String namespace() { return "ruby"; }

    public void declare(ContentDeclarations content) {
        content.item("ruby").rarity(Rarity.UNCOMMON).tab("minecraft:ingredients");
        content.item("ruby_apple").food(6, 1.2f).tab("minecraft:food_and_drinks");
        content.block("ruby_block").strength(5, 6).sound(BlockSound.METAL).requiresTool().light(3)
               .tab("minecraft:building_blocks");
    }
}
```

Declarations are **data**: id, stack size, durability, food, rarity, fire resistance, creative tab; for blocks, hardness, blast resistance, light, sound and tool requirement, plus an optional block item. The contract validates them (namespace and path syntax, `minecraft` namespace refused, duplicates refused, ranges) and orders them deterministically: by namespace, then declaration order. Everything a declaration can't express (models, textures, names, loot, recipes, tags, worldgen) is **data in the same jar**:

```
assets/ruby/items/ruby.json, models/item/ruby.json, textures/item/ruby.png, lang/en_us.json, blockstates/..., ...
data/ruby/loot_table/blocks/ruby_block.json, recipe/..., dimension/..., worldgen/structure/..., tags/...
data/minecraft/tags/block/mineable/pickaxe.json
mc_1.21.4/...            version overlay: replaces files only on 1.21.4 (formats do change between versions)
```

### 3.2 The runtime (per Minecraft version, inside the Conductor mod)

| Step | When | How |
|---|---|---|
| Discover content modules | before the game starts (the boot shim's pre-launch call; both client and dedicated server) | the same plain-jar discovery as other modules |
| Register blocks, block states, items, block items | `BuiltInRegistries.freeze()` HEAD (a Mixin) | vanilla's own calls: `Registry.register`, `Items.registerItem/registerBlock`, `Block.BLOCK_STATE_REGISTRY.add` |
| Creative tabs | `CreativeModeTab.buildContents` TAIL (a Mixin) | add each module item to the tab it asked for |
| Assets and data | `PackRepository` constructor (a Mixin) | one required pack per content jar, for client resources and server data, with the version overlay `mc_<version>` |

Why a hook at `freeze()` and not a redirect of the bootstrap (Fabric API's way): it runs immediately before the freeze **wherever the freeze happens**. That is at bootstrap without Fabric API, and later, at Fabric API's delayed freeze, with it. So the two don't fight over the same call.

**Failure rules** (Conductor must never crash the game):
- A content module that throws, or declares something invalid, is reported and skipped **as a whole**: half a module would desync ids.
- Registration that fails inside Minecraft is logged with the module's name. Because the registries can no longer be trusted, the game is then allowed to fail exactly as vanilla would. This is the one place where "never crash" can't hold: a half-registered registry is worse than a clear start-up error.

### 3.3 Client and server

- **Same modules on both sides.** A content module must be in `conductor/modules/` on the server *and* on every client: built-in content is sent as numbers (fact 6). This is what every loader requires too.
- **Data-only modules** (dimensions, structures, recipes, loot, worldgen using vanilla blocks) need the module **only on the server**. Vanilla clients can join, because that data is either server-only or synced by vanilla itself.
- **Next:** an id handshake (as in Fabric API's registry sync) so a mismatched client is told *which* module differs instead of failing obscurely. It is planned, not built.

## 4. Mobs (entities): phase 2

Custom mobs need more than declarations:
- an `EntityType` (registered like items);
- default attributes, held in a private immutable map, so a Mixin;
- spawn placement (a private `register`, so an invoker);
- spawn rules (biome JSON, i.e. data);
- a spawn egg (constructor differs between versions);
- on the client, a renderer and model layers. These are private registries, and the renderer API changed a lot between 1.21.4 and 1.21.11.

**Status:** step 1 is built (ZOMBIE, SPIDER and PIG archetypes) and verified live on both versions ([live-run-content.md](live-run-content.md) §5).

**Plan:**
1. **Declared mobs built on a vanilla archetype.** For example, "a zombie-like mob, 1.5× size, 40 health, this texture": `EntityType` plus attributes plus the archetype's AI and renderer with a module texture. Version-agnostic, since the module only declares.
2. **Behaviour.** AI goals as contract building blocks (wander, attack nearest, flee, follow) that the runtime maps to vanilla goals per version.
3. **Native modules** for anything beyond that. They are tied to one Minecraft version, but registered through the same pre-freeze window.

## 5. Converting existing mods

**Built (v1):** [`tools/convert`](../tools/convert/README.md). It does the report and the skeleton described below, and adds the 1.21.4+ item definitions that older mods lack. It also migrates data: tag entries for content not ported become optional, 1.21.1 recipes move to the 1.21.2+ format, and data that needs content not ported is left out and listed. It was tried on five real mods from Modrinth. One of them, Thaumon (Fabric, 1.21.1, 137 blocks), was converted and run on Conductor 1.21.11: 40 blocks, with 0 data errors: [live-run-content.md](live-run-content.md) §2.

The design:

`conductor-convert` (a command-line tool, no game needed) reads a Fabric, Quilt, Forge or NeoForge jar and produces:

1. **A readiness report.** For example: "items 12, blocks 8 (declarable: 7), block entities 1 (native), entities 2 (phase 2), mixins 5 (native), loader APIs used: fabric-item-group-api, fabric-object-builder-api, …". It comes from the jar's metadata, its classes (calls to `Registry.register`, `DeferredRegister`, loader API packages) and its `assets/`/`data/`.
2. **A content-module skeleton.**
   - `assets/` and `data/` copied, except loader-only JSON (Fabric load conditions, NeoForge data maps and biome modifiers), which is listed.
   - A `ContentModule` with declarations for the simple items and blocks it can recognise.
   - TODO markers for the rest.

Bridges like Sinytra Connector (Fabric mods on NeoForge) and Kilt (Forge mods on Fabric) show that running unmodified mods means re-implementing a whole loader API. That is a much larger, separate project, and the converter does not attempt it.

## 6. Mods that replace parts of the game engine (Sodium, Lithium, ...)

Project owner (2026-10-13): will Conductor also handle performance mods like Sodium?

Performance mods are a different category. **They are hosted next to Conductor, not as version-agnostic modules.**
- **What they are.**
  - Sodium replaces Minecraft's chunk renderer (meshing, GPU upload, culling).
  - Lithium replaces parts of the game logic.
  - Both do it with many Mixins written against one version's internals. That internal code is exactly what they improve, so it can't be described by a version-independent contract. Each version of such a mod has to be written for that version.
- **How Conductor hosts them.**
  - They stay ordinary mods, and Conductor's job is to **stay compatible** with them.
  - Conductor's hooks are deliberately small. One client-tick hook, chat and packet hooks, one injection after the world is drawn, and the content hooks above. None of them replaces a vanilla system.
  - The render hook (overlays) is the one most likely to meet Sodium. It needs a live compatibility run. Fixes, if any, go into Conductor's hooks, never into the other mod.
- **Joining the platform.** A performance mod that wants Conductor's settings, commands or module management joins as a **native module**, tied to its Minecraft version, as it is today.
- **Planned:** compatibility live runs with Sodium and Lithium on 1.21.11 (Conductor's overlays, events and content with each installed).

## 7. Order of work

1. **Done:** survey ([survey-content-apis.md](survey-content-apis.md)).
2. **Done:** contract: `ContentModule`, `ContentDeclarations`, validation, deterministic order; unit tests (`ContentPlanTest`).
3. **Done:** runtime: pre-freeze registration, creative tabs, packs with version overlays; the boot shim's pre-launch call; Conductor on dedicated servers (content only).
4. **Done:** example `examples/ruby-content`.
5. **Done:** live test, a Fabric dedicated server plus a client on 1.21.11 and 1.21.4 with the same jar ([live-run-content.md](live-run-content.md)).
6. **Done:** converter v1 (report plus skeleton), tried on real mods.
7. **Done:** blocks with states. `BlockShape` maps to vanilla's block classes: pillar, stairs, slab, wall, fence, fence gate, door, trapdoor, button, pressure plate, pane, leaves, carpet, glass. It comes with materials and render layers. The converter recognises shapes from blockstate files: on Thaumon, 127 of 137 blocks are declarable (was 40).
   - **Then:** facing cubes (FACING, HORIZONTAL_FACING) and blocks shaped by boxes (`box(...)`, turned with the block, waterloggable), plus mixed sounds by id. The converter takes the boxes from the block model's elements. It treats a block class as covered when it only overrides outline, facing, water, rotation, skylight and sounds. On Thaumon: **137 of 137** blocks declarable, and 7 of its 8 custom classes covered; live on 1.21.11 and 1.21.4 ([live-run-content.md](live-run-content.md) §3).
8. **Done:** declared mobs on vanilla archetypes (zombie, spider, pig). The converter maps a mod's entity classes that extend those to `content.mob(...).like(...)`.
9. **Next:** more archetypes, spawn eggs and natural spawning, the id handshake.
