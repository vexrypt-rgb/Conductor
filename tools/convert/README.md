# conductor-convert

Reads a Fabric, Quilt, Forge or NeoForge mod jar and tells you what it would take to run it on Conductor. It can also write a Conductor **content module** with everything that ports today. Python 3 only, no other dependencies. The jar is read, never run.

```sh
python3 tools/convert/conductor_convert.py some-mod.jar                 # readiness report (Markdown)
python3 tools/convert/conductor_convert.py some-mod.jar --out my-port   # report + content-module skeleton
python3 tools/convert/conductor_convert.py some-mod.jar --json          # the analysis as JSON
python3 -m unittest discover -s tools/convert                           # its tests (needs a JDK; build :contract first)
```

## What it reads

| From | It finds |
|---|---|
| `fabric.mod.json`, `quilt.mod.json`, `META-INF/mods.toml`, `META-INF/neoforge.mods.toml` | every loader the jar supports, mod id, version, Minecraft range, Mixin configs |
| `assets/` | items (`items/`, `models/item/`), blocks (`blockstates/`; full cubes vs blocks with states such as stairs and slabs), entities and names (`lang/en_us.json`) |
| `data/` | data by kind (recipes, loot, tags, worldgen, dimensions, ...), the pre-1.21 folder names the game now ignores, loader-only JSON (load conditions, NeoForge data maps, biome modifiers) |
| class files (constant pools) | classes extending Item, Block, Entity, BlockEntity, MobEffect, renderers, ... (custom behaviour); registration style; which loader APIs it uses; Mixins |

Fabric mods are compiled against intermediary names. The few Minecraft classes the tool needs are mapped in the script, checked against the 1.21.4 and 1.21.11 mappings.

## What the skeleton contains

- `assets/` and `data/`, minus loader-only JSON (the report lists what was left out).
- **Data migrated so it loads:**
  - tag entries for content that isn't ported become optional, so vanilla tags that include them still load;
  - pre-1.21.2 recipes move to the current ingredient format;
  - recipes, loot tables and advancements that need content that isn't ported are left out and listed.
  On Thaumon this took the server from 382 data errors to 0 ([live-run-content.md](../../docs/live-run-content.md) §2).
- The `assets/<ns>/items/<id>.json` item definitions that 1.21.4+ needs and older mods lack.
- A `ContentModule` declaring the items and the blocks, with TODOs for properties the jar doesn't state plainly (strength, sound, stack size, food, ...). Blocks whose states match no Conductor shape are listed as comments.
  - **Shapes** come from blockstate files: stairs, slabs, walls, doors, facing cubes and so on.
  - **Blocks shaped by boxes** (facing blocks whose model isn't a full cube) get one `.box(...)` per model element, turned to face north and cut to the block.
  - **Covered classes.** A block class that only overrides the outline, facing, water, rotation, skylight and sounds is listed as covered, not as custom behaviour; its mixed sounds become `.sounds(...)`.
- `META-INF/services/conductor.contract.ContentModule`, `build.gradle` and `README.md` (the report).

## Verdicts

- **READY:** everything found is data or declarable.
- **PARTIAL:** data and simple items and blocks port; some custom item or block behaviour, or blocks with states, stay behind.
- **NATIVE:** Mixins or other code need a native (version-specific) module; the data and simple content still port.

## Limits

- **Properties are not recovered.** Hardness, sounds, stack sizes and food values live in code; they get defaults plus a TODO.
- **Content without a model or name file is missed.** The counts come from assets.
- **Code is not translated.** Custom behaviour, entities, Mixins and loader events are reported with what to use instead, if anything exists yet.
