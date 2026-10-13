# Survey: how loaders host content mods, and what Conductor must do itself (MC 1.21.4 / 1.21.11)

Date: 2026-10-13. Labels: **[V]** Verified (I checked the bytecode, the Fabric source or an official doc myself),
**[R]** Reported (secondary source or memory, not re-checked), **[U]** Unknown.

Jars inspected with `javap` / `unzip`:
- 1.21.4: `/root/.gradle/caches/unimined/net/minecraft/minecraft/1.21.4/intermediary-mojmap-parchment-2025.03.23/minecraft-1.21.4-minecraft-none-merged+fixed-mojmap.jar` and `.../1.21.4/minecraft-1.21.4-client.jar`
- 1.21.11: `/root/.gradle/caches/unimined/net/minecraft/minecraft/1.21.11/intermediary-mojmap-parchment-2025.12.20/minecraft-1.21.11-minecraft-none-merged+fixed-mojmap.jar` and `.../1.21.11/minecraft-1.21.11-client.jar`

Fabric API source: sparse clone of `FabricMC/fabric` master at commit `0b50ffd` (2026-10-06). That commit is for MC **26.3**,
so the class names below come from that branch. The same design existed throughout 1.21.x [R].

---

## 1. Code-registered (built-in) vs data-driven (dynamic) content

### 1.1 Built-in registries: registered from Java code and frozen at bootstrap
`net.minecraft.core.registries.BuiltInRegistries` holds static `Registry`/`DefaultedRegistry` fields. You add to them with
`Registry.register(BuiltInRegistries.X, ResourceKey|Identifier, value)`, which delegates to
`MappedRegistry.register(ResourceKey, T, RegistrationInfo)` [V].
Content-relevant registries present in **both** 1.21.4 and 1.21.11 [V]:
`BLOCK, ITEM, ENTITY_TYPE, BLOCK_ENTITY_TYPE, FLUID, SOUND_EVENT, PARTICLE_TYPE, MOB_EFFECT, POTION, MENU,
RECIPE_TYPE, RECIPE_SERIALIZER, ATTRIBUTE, VILLAGER_PROFESSION, POINT_OF_INTEREST_TYPE, MEMORY_MODULE_TYPE, SENSOR_TYPE,
ACTIVITY, GAME_EVENT, CREATIVE_MODE_TAB, DATA_COMPONENT_TYPE, ENCHANTMENT_EFFECT_COMPONENT_TYPE, ENCHANTMENT_*_EFFECT_TYPE,
CONSUME_EFFECT_TYPE, DECORATED_POT_PATTERN, CUSTOM_STAT, STAT_TYPE, TRIGGER_TYPES, LOOT_*_TYPE, COMMAND_ARGUMENT_TYPE`
plus the **worldgen *type* registries**: `FEATURE, CARVER, STRUCTURE_TYPE, STRUCTURE_PIECE, STRUCTURE_PLACEMENT,
STRUCTURE_PROCESSOR, STRUCTURE_POOL_ELEMENT, PLACEMENT_MODIFIER_TYPE, BIOME_SOURCE, CHUNK_GENERATOR, DENSITY_FUNCTION_TYPE,
FOLIAGE_PLACER_TYPE, TRUNK_PLACER_TYPE, TREE_DECORATOR_TYPE, BLOCKSTATE_PROVIDER_TYPE, MATERIAL_RULE/CONDITION, ...`.
- Rule of thumb: a new *kind* of feature, structure, recipe or loot function (a codec plus Java logic) is a built-in
  registry entry. A configured *instance* of it is datapack JSON.
- 1.21.4 also has `CAT_VARIANT` and `FROG_VARIANT` as built-in registries. 1.21.11 no longer has them, because they became data-driven (see 1.2) [V].
- 1.21.11 adds built-in `GAME_RULE, DIALOG_TYPE, ENVIRONMENT_ATTRIBUTE, SPAWN_CONDITION_TYPE, SLOT_SOURCE_TYPE, PERMISSION_*,
  TEST_*`, among others [V].

### 1.2 Dynamic (datapack) registries: JSON loaded at world or server load
These are listed in `net.minecraft.resources.RegistryDataLoader` [V, read from the `<clinit>` of both jars].
- **WORLDGEN_REGISTRIES (1.21.4):** `dimension_type, worldgen/biome, chat_type, worldgen/configured_carver,
  worldgen/configured_feature, worldgen/placed_feature, worldgen/structure, worldgen/structure_set, worldgen/processor_list,
  worldgen/template_pool, worldgen/noise_settings, worldgen/noise, worldgen/density_function, worldgen/world_preset,
  worldgen/flat_level_generator_preset, trim_pattern, trim_material, trial_spawner, wolf_variant, painting_variant,
  damage_type, worldgen/multi_noise_biome_source_parameter_list, banner_pattern, enchantment, enchantment_provider,
  jukebox_song, instrument`.
- **1.21.11 adds:** `wolf_sound_variant, pig_variant, frog_variant, cat_variant, cow_variant, chicken_variant,
  zombie_nautilus_variant, test_environment, test_instance, dialog, timeline`.
- **DIMENSION_REGISTRIES:** `LEVEL_STEM` only (`data/<ns>/dimension/*.json`) [V].
- **SYNCHRONIZED_REGISTRIES** (sent to the client, see section 3) [V]:
  - 1.21.4: `biome, chat_type, trim_pattern, trim_material, wolf_variant, painting_variant, dimension_type, damage_type,
    banner_pattern, enchantment, jukebox_song, instrument`.
  - 1.21.11: the same, plus `wolf_sound_variant, pig/frog/cat/cow/chicken/zombie_nautilus_variant, test_environment,
    test_instance, dialog, timeline`.
  - Worldgen features, structures, noise and `LEVEL_STEM` stay **server-only**.
- **Reloadable server data** (`/reload`, outside RegistryDataLoader):
  - `loot_table`, `predicate` and `item_modifier` are loaded by `net.minecraft.server.ReloadableServerRegistries`
    (`scheduleRegistryLoad(LootDataType, ...)`) [V].
  - `recipe` goes through `RecipeManager`/`RecipeMap` [V class presence].
  - Also `advancement`, `function` and `tags/<registry>` [V dirs in jar].
  - Since 1.21.2, "`Recipe` and the recipe ID is now server-side only... Clients receive `RecipeDisplayEntry`" [V Fabric blog].
- Vanilla `data/minecraft/` directories in the client jar [V via `unzip -l`]:
  - 1.21.4: `advancement, banner_pattern, chat_type, damage_type, datapacks, dimension_type, enchantment,
    enchantment_provider, instrument, jukebox_song, loot_table, painting_variant, recipe, structure (NBT templates), tags,
    trial_spawner, trim_material, trim_pattern, wolf_variant, worldgen`.
  - 1.21.11 adds `cat/chicken/cow/frog/pig/wolf_sound/zombie_nautilus_variant, dialog, test_environment, test_instance, timeline`.
  - Vanilla ships no `data/minecraft/dimension`. Overworld, Nether and End come from `worldgen/world_preset` [V].
- Pack formats [V from `version.json`]:
  - 1.21.4: resource 46, data 61.
  - 1.21.11: resource 75.0, data 94.1. The `pack_version` keys became `resource_major/minor`, `data_major/minor`.

### 1.3 1.21.2+ registration-API changes (affect both targets)
- `Item.Properties#setId(ResourceKey<Item>)` and `BlockBehaviour.Properties#setId(ResourceKey<Block>)` exist in both jars.
  The strings "Item id not set" and "Block id not set" are in both, so the id must be set before the `Item`/`Block`
  constructor runs [V]. Fabric's 1.21.2 notes say "Registry keys must be set manually in every block and item's settings" [V blog].
- `Item.Properties#useBlockDescriptionPrefix()` gives BlockItems a `block.<ns>.<path>` translation key [V method; R semantics].
- `EntityType.Builder#build(ResourceKey<EntityType<?>>)` takes the key in both jars [V].
- Vanilla helpers `Items.registerItem(String, Function<Properties,Item>[, Properties])` and `Items.registerBlock(Block, ...)`
  are public static in both [V]. They register under `minecraft:` though, so mods copy the pattern with their own namespace.
- `Registry` now implements `HolderLookup`: `getEntry`→`getOptional`, `entryOf`→`getOrThrow` (yarn names in the blog) [V blog].
- 1.21.4 adds item model definitions in `assets/<ns>/items/<id>.json`. They select the model and carry tints, and `ItemColors` was removed [V].
  - Fabric blog: "items now utilize new item model definition json files in `assets/<namespace>/items`"; "`ItemColors` and related APIs for tinting items have been removed".
  - The `ItemColors` class is absent from both jars [V].
  - `DataComponents.ITEM_MODEL` exists in 1.21.4 [V].
  - `assets/minecraft/equipment/` (armor layers) exists in both [V].

Sources: https://fabricmc.net/2024/10/14/1212.html , https://fabricmc.net/2024/12/02/1214.html ,
https://minecraft.wiki/w/Data_pack

---

## 2. Registration and freezing: vanilla and each loader

### 2.1 Vanilla [V, bytecode of the 1.21.11 jar; same structure in 1.21.4]
- `Bootstrap.bootStrap()` calls, in order: `FireBlock.bootStrap`, `ComposterBlock.bootStrap`, ..., `DispenseItemBehavior.bootStrap`,
  `CauldronInteraction.bootStrap`, **`BuiltInRegistries.bootStrap()`**, `CreativeModeTabs.validate()`, `wrapStreams()`.
- `BuiltInRegistries.bootStrap()` is `createContents(); freeze(); validate(REGISTRY);`.
  - `createContents()` (private) runs each `LOADERS` supplier, which class-loads `Blocks`, `Items` and the others.
  - `freeze()` (private) freezes `REGISTRY` and then every sub-registry, after `bindBootstrappedTagsToEmpty`.
- `MappedRegistry` has `private boolean frozen` and `private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders`.
  - `register` throws "Registry is already frozen (trying to add key …)".
  - `freeze()` throws "Unbound values in registry …" and "Some intrusive holders were not registered: …", then sets
    `unregisteredIntrusiveHolders = null`.
  - After that, `createIntrusiveHolder` throws "This registry can't create intrusive holders".
  - Consequence: once frozen, you cannot even **construct** a new `Block`, `Item`, `EntityType` or `Fluid`, because their
    constructors call `createIntrusiveHolder`. Registering late is impossible without hacks.
- Where Bootstrap runs:
  - Client: `net.minecraft.client.main.Main.main` calls `Bootstrap.bootStrap` at offset 634, before `new Minecraft(GameConfig)` at 1396.
  - Server: `net.minecraft.server.Main.main` calls `Bootstrap.bootStrap` at offset 268, before the "server.properties" and datapack loading.
- **Fabric Loader** injects mod init on the client inside `Minecraft.<init>`, just before `Thread.currentThread()`. On the
  server it injects into `Main.main` before the "server.properties" load. Both points are **after** Bootstrap
  [V `EntrypointPatch.java`, fabric-loader master].
- So for Conductor's shim, `onInitializeClient` runs with registries **already frozen** unless something delays the freeze.
  Without Fabric API, nothing does.

### 2.2 Fabric (+ Fabric API `fabric-registry-sync-v0`) [V source]
- Mods call `Registry.register(BuiltInRegistries.ITEM, key, item)` directly in `onInitialize`. There is no DeferredRegister.
  Docs: https://docs.fabricmc.net/develop/items/first-item (they now show mojmap `Item.Properties#setId`).
- Freeze is delayed by Fabric API, not by Loader:
  - `BootstrapMixin` `@Redirect`s the `BuiltInRegistries.bootStrap()` call inside `Bootstrap.bootStrap` to `BuiltInRegistries.createContents()`.
  - `BuiltInRegistriesMixin` makes `createContents` idempotent with a `hasInitialised` guard.
  - After mod init, the registries are frozen: on the client `MinecraftMixin` injects into `Minecraft.<init>` at
    `Thread.currentThread()`, and on the server `MainMixin` injects into `Main.main` at `Util.startTimerHackThread()`.
    Each calls `BuiltInRegistries.bootStrap()`, `BlockInitTracker.postFreeze()` and `CreativeModeTabs.validate()`.
- It also tracks block-state ids (`StateIdTracker` over `Block.BLOCK_STATE_REGISTRY`, an `IdMapper`) and BlockItem maps.
- **Registry sync:**
  - In the configuration phase, `RegistrySyncManager.configureClient` adds a `SyncConfigurationTask` that sends the
    server's name→raw-id map for every registry marked `MODDED` and `SYNCED`.
  - The client remaps its raw ids to match, and unmaps them on disconnect (`MinecraftMixin.disconnect`).
  - Clients without the channel are disconnected unless every synced registry is `OPTIONAL`.
  - Sync is skipped for the singleplayer owner.
  - Attributes: `SYNCED, MODDED, OPTIONAL, SAVE_DATA_VALIDATED`.
- Helpers: `FabricDefaultAttributeRegistry`, `FabricEntityType`, BlockEntity type builder (object-builder-api),
  `EntityRendererRegistry`/`ModelLayerRegistry`, `BlockColorRegistry`, `BlockEntityRendererRegistry`, and in 1.21.x `BlockRenderLayerMap`
  [V names on the 26.3 branch; 1.21.x names R].

### 2.3 Forge (MinecraftForge, 1.21.x) [V doc]
- `DeferredRegister` "maintains a list of suppliers for entries and registers the objects from those suppliers during `RegisterEvent`".
- `RegisterEvent` "is fired for each registry after the mod constructors and before the loading of configs".
- Lookup goes through `RegistryObject`/`@ObjectHolder`. Datapack registries come from `DataPackRegistryEvent.NewRegistry`, and
  "Datapack Registries cannot be created with DeferredRegister". https://docs.minecraftforge.net/en/1.21.x/concepts/registries/
- FML launches mods before vanilla Bootstrap freezes, and Forge patches registries to unfreeze and refreeze around events.
  Forge registries sync ids to clients ("automatic syncing between sides") [R / partially V doc].

### 2.4 NeoForge [V doc]
- `DeferredRegister` (a wrapper over `RegisterEvent`) with `DeferredHolder`. `RegisterEvent` fires per registry "after the
  mod constructors". The docs only warn: "Query operations are only safe to use after registration has finished".
- Custom registries: `RegistryBuilder.sync(true)` for int-id networking, `NewRegistryEvent`.
- Datapack registries: `DataPackRegistryEvent.NewRegistry` with an optional network codec ("if null, registry entries will not be synced").
  https://docs.neoforged.net/docs/concepts/registries/
- **Data maps** are "data-driven, reloadable objects that can be attached to a registered object".
  - Path: `<ns>/data_maps/<registryPath>/<map>.json`.
  - They can be `#synced`; a "mandatory" flag disconnects clients that lack them, including vanilla clients.
  - Built-in maps such as compostables, furnace fuels and oxidizables replace hardcoded vanilla maps [R].
  - https://docs.neoforged.net/docs/resources/server/datamaps/
- Vanilla clients may join a NeoForge server only when no non-vanilla synced registry content or required payloads are
  involved [R, from the 20.4 networking rework; not confirmed in current docs].

### 2.5 Quilt [R]
- QSL `registry` module: registry sync with per-entry exclusion, `RegistryMonitor` callbacks, and **Registry Entry
  Attachments**, which are data-driven per-entry values like NeoForge data maps [V README feature table only].
- For 1.21 Quilt relies on Quilted Fabric API, so most mods use Fabric API semantics. QSL development has largely stalled
  [R: CurseForge lists a QSL 10.0.0-alpha.1 build for 1.21, dated 2024-07-14].
  https://github.com/QuiltMC/quilt-standard-libraries

### 2.6 What a loader-free Conductor must do (design implications)
1. **Delay the freeze, as Fabric API does.** Using Conductor's own SpongePowered Mixins:
   - `@Redirect` `BuiltInRegistries.bootStrap()` in `Bootstrap.bootStrap` to an `@Invoker` of the private `createContents()`.
   - Guard `createContents` against running twice.
   - Run Conductor content registration.
   - Call `BuiltInRegistries.bootStrap()` yourself, then `CreativeModeTabs.validate()` (also skipped by the redirect).
   - This must happen **before** `new Minecraft(...)` finishes on the client and before `server.properties` load on the
     server. Conductor's current hook point, `onInitializeClient` inside `Minecraft.<init>`, is still before
     `Thread.currentThread()`, i.e. before Fabric API's freeze point [V positions; ordering vs Fabric API's injector U].
2. **Coexisting with Fabric API.** If Fabric API is installed alongside the shim, both would redirect the same call.
   Conductor should detect Fabric API (or check `MappedRegistryAccessor.isFrozen()` at init) and, if it is present,
   just register while unfrozen [design; U whether two redirects conflict, since Mixin rejects duplicate @Redirect targets → likely crash].
3. **Unfreeze-and-refreeze is the alternative.** Set `frozen=false`, recreate `unregisteredIntrusiveHolders`, register,
   then `freeze()` again. It is fragile: `freeze()` rebinding tags may throw "Tags already present before freezing", and
   holder and tag state must be reset [V strings; behaviour U].
4. **Registration must be deterministic and identical on client and server** (see section 3), or Conductor must
   implement its own id-sync handshake in the configuration phase, as Fabric's `RegistrySyncManager` does.
5. **Conductor currently initialises from a client entrypoint only.** Content hosting also needs a dedicated-server
   entrypoint, i.e. the shim's `main` entrypoint or a Mixin into `net.minecraft.server.Main`.

---

## 3. Client/server synchronisation of registry content

- **Dynamic registries** [V]:
  - The server sends `SYNCHRONIZED_REGISTRIES` in the configuration phase with `ClientboundRegistryDataPacket`, built by
    `RegistrySynchronization.packRegistries(ops, access, Set<KnownPack>, ...)`.
  - With `ClientboundSelectKnownPacks`/`ServerboundSelectKnownPacks`, entries that come from a pack the client also has
    (e.g. `minecraft:core`) are sent without their data. Everything else is sent in full [V classes; R semantics].
  - Fabric's `KnownPacksManagerMixin` deals with mod packs here [V file exists].
- **Tags** are sent in `ClientboundUpdateTagsPacket`. `TagNetworkSerialization$NetworkPayload` is a
  `Map<Identifier, IntList>`, so tag members travel as **raw int ids** [V].
- **Built-in registries are NOT synced by vanilla.**
  - Items, blocks, entity types and other built-in entries go over the wire as raw int ids via
    `ByteBufCodecs.registry(...)`/`holderRegistry(...)`/`idMapper(...)`; `ItemStack`'s stream codec uses `Registries.ITEM` [V].
  - Block states use the global `Block.BLOCK_STATE_REGISTRY` `IdMapper` (chunk palettes) [V].
  - Raw ids are assigned in registration order [R], so both sides must register exactly the same entries in the same
    order, or run an id-remap handshake like Fabric registry sync, Forge or NeoForge.

Consequences:
| Scenario | Outcome |
|---|---|
| Vanilla client → server adding **built-in** content (items, blocks, entities, block entities, particles, sounds, menus, effects) | Unknown raw ids fail to decode or map to the wrong objects. Fabric disconnects such clients up front [V Fabric code]. Without a sync layer, expect decode errors or a disconnect [R]. |
| Vanilla client → server adding only **synced dynamic** content (biome, dimension_type, enchantment, damage_type, painting/wolf/... variant, jukebox_song, trim, banner_pattern, instrument, chat_type) | Works: the data is sent in full. Client-side assets for new textures (painting, trim, wolf) or translations are missing unless a server resource pack supplies them [R]. |
| Vanilla client → server with new **server-only** data (dimensions/level_stem, worldgen features, structures, recipes on vanilla items, loot tables, advancements, functions) | Works. Since 1.21.2 recipes are server-side and the client only receives display entries [V blog]. |
| Modded client (same content) → vanilla server | Works if mod entries come after vanilla ones, so vanilla raw ids are unchanged. Fabric unmaps on disconnect [V]. Ordering guarantee R. |
| Modded client ↔ modded server, different content set or order | Desync without an id-sync handshake [R]. |

"Server-side content" libraries such as Patbox's Polymer [R] reach vanilla clients by sending vanilla items and blocks
with components like `minecraft:item_model` (1.21.4+) and a server resource pack. Conductor could use that as a
vanilla-client-compatible mode.

---

## 4. Entities

Vanilla mechanics in both jars [V]:
- **Type:** `EntityType.Builder.of(EntityFactory, MobCategory)` with `.sized(...)` etc., then `.build(ResourceKey)`, then
  register into `BuiltInRegistries.ENTITY_TYPE`.
- **Attributes:** `DefaultAttributes.SUPPLIERS` is a private static **`ImmutableMap`**, read via `getSupplier`/`hasSupplier`.
  `DefaultAttributes.validate()` checks living types.
  - A loader-free host must Mixin `getSupplier`/`hasSupplier` (or `@ModifyVariable` the map) to add modded suppliers.
  - Fabric uses `FabricDefaultAttributeRegistry.register(type, builder)` [V docs page]; Forge/NeoForge use `EntityAttributeCreationEvent` [R].
- **Spawn placement:** `SpawnPlacements.DATA_BY_TYPE` is a private `HashMap`, and `register(...)` is private static. Call it
  via an `@Invoker`. NeoForge has `RegisterSpawnPlacementsEvent` [V doc].
  Natural spawning itself lives in biome JSON `spawners`, which is data-driven. Changing it means a biome override or a
  biome-modification hook (Fabric `BiomeModifications`, NeoForge biome modifier JSON) [R].
- **Renderers (client):** `EntityRenderers.PROVIDERS` is a private static `Object2ObjectOpenHashMap`, and `register(...)` is private static.
  `createEntityRenderers(Context)` builds the renderers on resource reload. Add providers before the first reload with an
  `@Invoker` on `register`. Fabric uses `EntityRendererRegistry` / `EntityRenderers.register` [V docs page].
  NeoForge uses `EntityRenderersEvent.RegisterRenderers#registerEntityRenderer` [V doc].
- **Model layers:** `LayerDefinitions.createRoots()` returns a freshly built `ImmutableMap<ModelLayerLocation, LayerDefinition>`.
  Inject at RETURN and wrap it in a new map with the modded layers. Fabric uses `ModelLayerRegistry` (EntityModelLayerRegistry in 1.21.x [R]).
  NeoForge uses `EntityRenderersEvent.RegisterLayerDefinitions#add` [V doc].
- **Render-state API:** since 1.21.2, renderers use `EntityRenderState` [R]. In 1.21.11 player rendering is `AvatarRenderer`
  (`createAvatarRenderers`) [V], and there is a new submit/"render node" pipeline [R, Fabric 26.x names `SubmitRenderPhase`].
  Renderer code is the least portable part across 1.21.4 and 1.21.11.
- **Spawn eggs:**
  - 1.21.4: `new SpawnEggItem(EntityType<? extends Mob>, Item.Properties)`. The item JSON uses `item/template_spawn_egg` with two `minecraft:constant` tints [V].
  - 1.21.11: `new SpawnEggItem(Item.Properties)` plus `Item.Properties#spawnEgg(EntityType)`, which stores the type in the
    `ENTITY_DATA` component. Each egg has its own texture: `pig_spawn_egg.json` → `minecraft:item/pig_spawn_egg`, no tints [V].
- `BlockEntityRenderers.register` is a private static `HashMap` registration in both versions [V]. 1.21.11 has a
  `BlockEntityRendererProvider<T, S extends BlockEntityRenderState>` signature [V].

---

## 5. Assets and data packaging

- **Fabric:**
  - `fabric-resource-loader` injects into `PackRepository.<init>` (`PackRepositoryMixin`) and, when a WORLD/SERVER
    `FolderRepositorySource` is present, adds a `ModResourcePackCreator` `RepositorySource`.
  - Every mod jar becomes a `Pack` for `assets/` (client) or `data/` (server), enabled by default at position TOP
    (`PackSelectionConfig(required=true, TOP, fixed=false)`) [V source].
  - Optional bundled packs go under `resourcepacks/<name>/` and are registered with
    `ResourceManagerHelper.registerBuiltinResourcePack(id, container, activationType)` [V javadoc via search].
    https://maven.fabricmc.net/docs/fabric-api-0.136.0+1.21.11/net/fabricmc/fabric/api/resource/ResourceManagerHelper.html
- **Forge/NeoForge:** each mod file is exposed as a resource and data pack (a "mod resources" pack), with
  `AddPackFindersEvent` for extra packs [R].
- **Vanilla mechanisms without a loader** [V classes and strings]:
  - `PackRepository(RepositorySource...)` with `reload()`, `setSelected(Collection<String>)` and `addPack(String)`.
    A Mixin on `<init>` that appends a custom `RepositorySource` is all that's needed, which is what Fabric does.
  - `ServerPacksSource` / `BuiltInPackSource` (`VANILLA_ID`, `discoverPacksInPath`, `createBuiltinPack`) discover the
    bundled feature packs in `data/minecraft/datapacks/` (bundle, trade_rebalance, ...). The client equivalent is `ClientPackSource`.
  - World `datapacks/` folder (`FolderRepositorySource` with `PackSource.WORLD`). Selection is stored in level.dat.
    `server.properties` has `initial-enabled-packs` and `initial-disabled-packs`.
  - Server resource pack: `server.properties` keys `resource-pack, resource-pack-id, resource-pack-sha,
    resource-pack-prompt, require-resource-pack`, plus `ClientboundResourcePackPushPacket`/`PopPacket` (multiple packs since 1.20.3 [R]).
  - Important: worldgen and dimension JSON is read when a world is **created or loaded** (`WorldLoader`/RegistryDataLoader). The mod
    pack must be selected in the world's `WorldDataConfiguration`. Fabric marks mod packs as required [V].
- **Expected mod-jar layout:**
  - `assets/<ns>/{blockstates,models/block,models/item,items(1.21.4+),textures,lang,sounds.json,sounds/,particles/,equipment}`.
  - `data/<ns>/{recipe,loot_table,tags/<registry>,worldgen/...,dimension,dimension_type,enchantment,damage_type,...}`.
  - `data/minecraft/tags/block/mineable/pickaxe.json` etc. for tool tags.

---

## 6. Blocks

- **Block:** `new Block(BlockBehaviour.Properties.of().setId(key)...)`, registered into `BuiltInRegistries.BLOCK`.
  The BlockItem is `new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())` (the vanilla `Items.registerBlock` pattern) [V signatures].
- **Block entities:** the `BlockEntityType(BlockEntitySupplier, Set<Block>)` constructor is **private** in both jars, and so is the
  static `register` [V]. This needs an `@Invoker`/accessor. Fabric provides a `FabricBlockEntityTypeBuilder` equivalent [R].
  Register into `BLOCK_ENTITY_TYPE` and then the renderer via `BlockEntityRenderers.register` (private, see section 4).
- **Render layer (cutout/translucent):** `ItemBlockRenderTypes.TYPE_BY_BLOCK` is a private static `HashMap` [V].
  - 1.21.4 values are `RenderType` (`RenderType.cutout()` etc.).
  - 1.21.11 values are `ChunkSectionLayer`; `getChunkRenderType` returns `ChunkSectionLayer` and `setFancy` became `setCutoutLeaves` [V].
  - Loader-free: `TYPE_BY_BLOCK.put(block, layer)` through an accessor. Fabric 1.21.x uses `BlockRenderLayerMap`, NeoForge
    uses `render_type` in the model JSON [R].
- **Colours:** `BlockColors.register(BlockColor, Block...)` is public, on the instance from `Minecraft.getBlockColors()` [V method].
  Item tints are no longer code in 1.21.4+: use the `tints` array in `assets/<ns>/items/<id>.json`, e.g.
  `oak_leaves.json` has a `minecraft:constant` tint in both jars [V].
- **Models:** `assets/<ns>/blockstates/<id>.json` → `models/block/*.json`. 1.21.11 introduces a `BlockStateModel`/
  `BlockModelDefinition`/`BlockStateModelLoader` pipeline [V classes] for baking, but the JSON formats stay compatible [R].
  The block's item needs `assets/<ns>/items/<id>.json` in 1.21.4+ [V].
- **Data:** loot table at `data/<ns>/loot_table/blocks/<id>.json`, plus mining tags. `Block#getLootTableKey` is `Optional` since 1.21.2 [V blog].

---

## 7. 1.21.4 vs 1.21.11 differences relevant to content

| Topic | 1.21.4 | 1.21.11 |
|---|---|---|
| Id class (Mojang mappings) | `net.minecraft.resources.ResourceLocation` [V] | `net.minecraft.resources.Identifier` [V]. NeoForge release notes call it a mechanical rename [R]: https://neoforged.net/news/21.11release/ |
| Item/Block `setId`, `EntityType.Builder.build(key)` | present [V] | present [V] |
| Item model definitions `assets/<ns>/items` | present, 1385 vanilla files [V] | present, 1505 files [V] |
| Spawn egg | `SpawnEggItem(EntityType, Properties)`, tinted template [V] | `SpawnEggItem(Properties)` + `Properties.spawnEgg(type)`, per-egg textures [V] |
| Chunk render layer | `RenderType` [V] | `ChunkSectionLayer` (+ `rendertype.RenderType` package) [V] |
| Cat/frog variants | built-in registries [V] | datapack registries, plus pig/cow/chicken/wolf_sound/zombie_nautilus variants [V] |
| New dynamic registries | — | dialog, timeline, test_environment, test_instance [V] |
| Pack format | resource 46 / data 61 [V] | resource 75.0 / data 94.1 [V] |
| Player renderer | `createPlayerRenderers` (PlayerSkin.Model) [V] | `createAvatarRenderers` (PlayerModelType) [V] |
| BlockEntityRendererProvider | `<T>` [R] | `<T, S extends BlockEntityRenderState>` [V] |
| Mappings | official + intermediary | last obfuscated version. Yarn/Intermediary stop after 1.21.11, and 26.1 is unobfuscated [R, Fabric 2025-12-05 post: https://fabricmc.net/2025/12/05/12111.html] |

A Conductor content API should hide `ResourceLocation`/`Identifier` behind its own id type, and hide render layer,
spawn-egg and renderer registration behind per-version runtime adapters (the existing `runtime/1.21.4` and `runtime/1.21.11` split).

---

## 8. Converting existing mods

**Mechanically portable** (mostly verbatim, after per-version format checks):
- `assets/`: textures, block and item models, blockstates, `items/` definitions, lang, sounds.json, particles JSON.
- `data/`: recipes, loot tables, tags, worldgen JSON, dimensions, enchantments, damage types, variants, advancements, structure NBT.
- Caveats:
  - These formats change between versions; the pack-format bumps above show it.
  - Loader-specific JSON does not port: NeoForge `data_maps/`, `neoforge:biome_modifier`, `neoforge:conditions`, Fabric
    `fabric:load_conditions`, Forge `forge:conditions`, and custom model loaders. A converter would have to translate or drop them [R].

**Needs code translation:**
- Registration: `Registry.register` (Fabric) ≈ direct, while `DeferredRegister`/`RegisterEvent` (Forge/NeoForge) become
  eager registration into Conductor's pre-freeze window.
- Loader APIs: attributes, renderers, layers, block render layers, creative tabs (Fabric `ItemGroupEvents`/
  `CreativeModeTabEvents`, NeoForge `BuildCreativeModeTabContentsEvent`), biome modification, networking,
  data generation, events, capabilities/attachments and transfer APIs.
- Mixins: these are portable only when they target vanilla (mojmap-named) code.
  - Forge/NeoForge mods also patch against Forge-modified methods.
  - Fabric mods compiled against intermediary names need remapping to Mojang names (Conductor's runtime) [R].

**Existing bridges:**
- **Sinytra Connector** runs Fabric mods on NeoForge, together with **Forgified Fabric API**, a "direct port of Fabric API
  for NeoForge... not an abstraction layer" [V README].
  - At load time it transforms Fabric jars: intermediary→Mojmap remapping, converting `fabric.mod.json` metadata and
    access wideners, and patching mixins that clash with NeoForge patches through the data-driven **Adapter** project [R].
  - https://github.com/Sinytra/Connector , https://github.com/Sinytra/ForgifiedFabricAPI
- **Kilt** runs (Neo)Forge mods on Fabric. It "recreates FML in a way that functions with Fabric Loader", bundles the
  Forge API and reimplements Forge patches as Mixins ("injects").
  - It "remaps them from the Forge SRG format into Fabric's Intermediary format, then applies some of its own fixers" [V README].
  - It is very experimental, with 1.20.1 and 1.21.1 builds.
  - https://github.com/KiltMC/Kilt
- **Architectury API + Architectury Loom/Plugin** support multiplatform builds at compile time.
  - Architectury API "abstract[s] calls to fabric api and forge api", with 90+ event hooks, networking, and a "game registry
    abstraction" (`DeferredRegister`/`RegistrySupplier`) [V README].
  - `@ExpectPlatform` swaps in static method implementations per platform.
  - Mods are written once against it, not converted after the fact. https://github.com/architectury/architectury-api
- **Implication for Conductor:**
  - The pragmatic path is a Conductor-native content API: an id type, registration in the pre-freeze window, client hooks,
    and pack injection.
  - Bridges in the style of Connector or Kilt (reimplementing the Fabric API or FML surface on Conductor) are large
    efforts and a long tail of compatibility work [R from both projects' stated instability and scope].

## Open questions / Unknown
- [U] Whether Conductor's Mixin redirect of `BuiltInRegistries.bootStrap` can coexist with Fabric API's identical `@Redirect` when both are loaded.
- [U] Exact behaviour of a vanilla 1.21.x client receiving an out-of-range item raw id: is it an exception/disconnect or `air`? Needs a live test.
- [U] Safety of the unfreeze/refreeze approach given tag binding in `MappedRegistry.freeze()`.
- [R→verify] NeoForge's current rules for vanilla-client connections. Fabric's 1.21.x-branch class names (`EntityModelLayerRegistry`, `BlockRenderLayerMap`, `ColorProviderRegistry`).
