# Live run: a content module on a Fabric dedicated server and client, 1.21.11 and 1.21.4 (2026-10-13)

**Module:** [`examples/ruby-content`](../examples/ruby-content). It is one plain jar (SHA-256 `b1f9d78f…f635`), **the same file on both versions**. It contains:
- **declared** in code: an item (`ruby:ruby`, UNCOMMON), a food (`ruby:ruby_apple`) and a block (`ruby:ruby_block`: strength 5/6, metal sound, light 3, needs a tool);
- **data:** the block's loot table, two recipes, the `mineable/pickaxe` and `needs_iron_tool` tags, and a dimension (`ruby:ruby_world`) whose ground is the module's block;
- **assets:** item and block models, three textures, English names.

It has no `pack.mcmeta`.

**Setup** (`scripts/prod-run.sh` with `SERVER_MODS`):
- The **server** is a Fabric dedicated server (KnotServer, production classpath) with only the Conductor mod. The module is in its `conductor/modules`.
- The **client** is a production Fabric client with the Conductor mod, the same content module, and `example-block-highlighter` set to `block=ruby:ruby_block`.
- Neither side has Fabric API or any other mod.

The server runs these commands after the player joins:
1. `give` 9 rubies, 3 ruby apples and 2 ruby blocks, then `clear Dev ruby:ruby 0`.
2. `setblock` three ruby blocks.
3. `recipe give Dev ruby:ruby_block`.
4. `loot give Dev mine …` twice.
5. After 12 s, `execute in ruby:ruby_world run tp Dev 0.5 -59 0.5`.
6. `execute at Dev if block ~ ~-1 ~ ruby:ruby_block run say …`.

**Evidence:**
- logs: `docs/evidence/module-runs/content-ruby-client-<version>.log.gz` and `content-ruby-fabric-server-<version>.log.gz`;
- screenshots: `docs/evidence/screenshots/content-ruby-<version>-{11,26}.png`.

| Check | 1.21.11 | 1.21.4 |
|---|---|---|
| Server: `Conductor content: registered 1 block(s) and 3 item(s) from 1 module(s) [ruby]` (before the registries froze) | yes | yes |
| Server: `Found new data pack conductor/ruby, loading it automatically` | yes | yes |
| `give`: `Gave 9 [item.ruby.ruby]`, 3 ruby apples, 2 ruby blocks; `clear … 0`: `Found 9 matching item(s)` | yes | yes |
| `setblock ruby:ruby_block` × 3 | `Changed the block` × 3 | same |
| Recipe from the module's data | `Unlocked 1 recipe(s)` | `Unlocked 1 recipes` |
| Loot table from the module's data | `Dropped 1 [block.ruby.ruby_block] from loot table ruby:blocks/ruby_block` (×2) | same |
| The module's dimension | `Teleported Dev` into `ruby:ruby_world`; `the ground here is ruby:ruby_block` | same |
| Client: same registration, and the module's resource pack loaded (`Reloading ResourceManager: vanilla, conductor/ruby`) | yes | yes |
| Client and server agree on block ids: the server's block updates decode on the client as `ruby:ruby_block` (Conductor's block events) | 3 of 3 | 3 of 3 |
| Client `findBlocks(ruby:ruby_block)` in the module's dimension | 64 found at y = −60: the ground | same |
| Textures, models, names (screenshots) | ruby, ruby apple and ruby block icons; placed blocks textured; chat shows `[Ruby]` in rarity colour, `[Ruby Apple]`, `[Block of Ruby]` | same |
| Model, texture or registry errors in the client log | none | none |

Notes:
- The server log shows `item.ruby.ruby`: a dedicated server has no language files. Names are translated on the client, as the screenshots show.
- `loot … mine` with a wooden pickaxe still dropped the block. The loot command reads the table directly; the "needs the right tool" rule applies when a player breaks the block. That was not exercised here.
- `No key layers in MapLike[{}]` comes from the test server's own `server.properties` (a flat world with empty settings). It appears in earlier runs without Conductor content too.
- Not checked live: placing the creative-tab entries in the creative inventory, a vanilla client joining (expected to fail for declared content; see CONTENT-MODS §3.3), or Fabric API being installed alongside.

**Test-setup fixes found by this run:**
- `prod_classpath.py` now lets the loader's ASM win on server classpaths as well; the 1.21.4 server bundles asm 9.6.
- `prod-run.sh` now notices a server that dies at start-up instead of waiting forever.

## 2. A real mod, converted: Thaumon (Fabric, 1.21.1) on Conductor 1.21.11

**Steps:**
1. `tools/convert/conductor_convert.py` read Thaumon 2.3.0+1.21 (Fabric, for 1.21.1, from Modrinth). Its verdict was PARTIAL:
   - 137 blocks: 40 full cubes and 97 with states (stairs, slabs, pillars, doors, ...);
   - 7 custom block classes and 1 custom item class;
   - Fabric API (item groups, render layers, datagen).
2. Its skeleton was compiled against the contract and zipped into a module jar, unchanged. The jar has 40 blocks and 1 item declared with default properties, plus Thaumon's assets and data.
3. The jar ran on a Fabric dedicated server and a client (1.21.11, production launch). Neither had Thaumon itself or Fabric API. The jar went in `conductor/modules` on both.

Thaumon's files were only used locally for this test. Nothing of it is in this repository except the run logs.

| | First try | After the converter's data fixes |
|---|---|---|
| Registered | 40 blocks, 41 items, on both sides | same |
| Data pack loaded automatically | yes | yes |
| Server `ERROR` lines | **382** | **0** (plus the test server's own `No key layers`) |
| `give`, `setblock` × 12, `loot … mine` from Thaumon's loot table | work | work |
| Textures and models on the client | rendered | rendered |
| Client model or texture errors | 0 | 0 |

**What caused the 382 errors** (each is now handled by the converter and covered by its tests):
1. **Tags that list content not ported.** Vanilla drops a whole tag when one entry is unknown. Tags that *include* that tag then fail too: `minecraft:logs`, `minecraft:mineable/pickaxe`, `minecraft:stairs`, ...
   - That breaks vanilla gameplay as well, not just the mod.
   - The converter now makes those entries optional (`"required": false`): 275 entries.
2. **The 1.21.2 recipe format.** Ingredients changed from `{"item": "x"}` to `"x"`. 48 recipes were migrated.
3. **Recipes, loot tables and advancements for content not ported** (the 97 blocks with states). These 338 files are now left out and listed in the report.

**Still not right:**
- Thaumon's glass-like blocks render opaque: Conductor has no render layers yet.
- All blocks use default strength and sound until the TODOs in the generated module are filled in.

Evidence: `docs/evidence/module-runs/converted-thaumon-{fabric-server,client}-1.21.11.log.gz`.

## 3. Blocks with states (2026-10-13, later)

**Module:** the ruby jar now also declares `ruby_stairs` (`stairsOf("ruby_block")`), `ruby_slab`, `ruby_wall`, `ruby_pillar`, `ruby_glass` and `ruby_door` (WOOD). Their blockstate, model and loot JSON were made from vanilla's own files for stone brick stairs/slab/wall, quartz pillar, glass and oak door, with the ids renamed. The JSON derived from 1.21.4 and 1.21.11 is byte-identical, so no version overlay was needed.

The setup is the same as §1: a Fabric dedicated server plus client, both versions, the same jar. Evidence: `content-shapes-{fabric-server,client}-<version>.log.gz` and `screenshots/content-shapes-<version>.png`.

| Check | 1.21.11 | 1.21.4 |
|---|---|---|
| Registered | 7 blocks, 9 items | same |
| A wall placed next to `ruby_block` connects (`ruby_wall[west=low]`) | CHECK passed | passed |
| The door has both halves (`half=upper` above `half=lower`) | passed | passed |
| `ruby_slab[type=double]` and `ruby_pillar[axis=x]` | passed | passed |
| Loot: double slab, stairs, glass without silk touch, door | `Dropped 2 [ruby_slab]`, 1 stairs, **0** glass, 1 door | same |
| Client: `2 block(s) drawn with a see-through render layer` (glass, door) | yes | yes |
| Screenshot | the door's window and the glass are see-through; slabs, stairs and the sideways pillar look as in vanilla | same |
| Model or texture errors in the client log | 0 | 0 |

The loot lines come from the first run of this test. In the rerun with the corrected state checks, the player had moved before the `loot … mine` commands, which then hit air.

**Thaumon again, with shapes:**
- The converter recognised the shapes from Thaumon's blockstate files: **127 of 137 blocks** declared (40 before). That is 15 stairs, 15 slabs, 19 pillars, 10 walls, 5 fences, 2 fence gates, 4 doors, 3 trapdoors, 4 buttons, 4 pressure plates, 6 panes, 5 glass-like blocks and 35 cubes.
- The remaining 10 are its 7 custom-code blocks and 3 rotatable stones.
- Run on 1.21.11:
  - 127 blocks and 128 items registered on both sides;
  - 18 blocks drawn see-through;
  - **0 data errors** (the only `ERROR` is the test server's `No key layers`);
  - 0 model errors.
- Screenshot (not committed: third-party textures): a wall, a door with see-through windows, a bookshelf, bricks, a sideways log, stairs, a slab, and fences joined to each other.
- Evidence: `converted-thaumon-shapes-{fabric-server,client}-1.21.11.log.gz`.
