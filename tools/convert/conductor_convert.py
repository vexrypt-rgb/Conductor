#!/usr/bin/env python3
"""conductor-convert: read a Fabric, Quilt, Forge or NeoForge mod jar and say what it would take to run it on Conductor.

    python3 conductor_convert.py <mod.jar> [--out <dir>] [--json]

Without --out it prints a readiness report. With --out it also writes a Conductor content-module skeleton:
  - the mod's assets/ and data/, minus loader-only JSON (listed in the report);
  - a ContentModule declaring the items and blocks it found;
  - META-INF/services, build.gradle and README.md (the report).

The jar is only read, never run. Classes are inspected through their constant pools (which classes they extend and
reference), with no third-party library. Fabric mods are compiled against intermediary names; the few Minecraft
classes the report needs are mapped below. They were checked against the 1.21.4 and 1.21.11 intermediary and Mojang
mappings, and intermediary names are stable across versions by design.
"""
import argparse
import json
import os
import re
import shutil
import struct
import sys
import zipfile

try:
    import tomllib
except ImportError:  # Python < 3.11
    tomllib = None

# Mojang name -> intermediary name (Fabric). Verified for 1.21.4 and 1.21.11.
INTERMEDIARY = {
    "net/minecraft/core/Registry": "net/minecraft/class_2378",
    "net/minecraft/core/registries/BuiltInRegistries": "net/minecraft/class_7923",
    "net/minecraft/core/registries/Registries": "net/minecraft/class_7924",
    "net/minecraft/world/item/Item": "net/minecraft/class_1792",
    "net/minecraft/world/item/BlockItem": "net/minecraft/class_1747",
    "net/minecraft/world/level/block/Block": "net/minecraft/class_2248",
    "net/minecraft/world/entity/EntityType": "net/minecraft/class_1299",
    "net/minecraft/world/entity/Entity": "net/minecraft/class_1297",
    "net/minecraft/world/entity/LivingEntity": "net/minecraft/class_1309",
    "net/minecraft/world/entity/Mob": "net/minecraft/class_1308",
    "net/minecraft/world/level/block/entity/BlockEntityType": "net/minecraft/class_2591",
    "net/minecraft/world/level/block/entity/BlockEntity": "net/minecraft/class_2586",
    "net/minecraft/world/item/CreativeModeTab": "net/minecraft/class_1761",
    "net/minecraft/sounds/SoundEvent": "net/minecraft/class_3414",
    "net/minecraft/core/particles/ParticleType": "net/minecraft/class_2396",
    "net/minecraft/world/effect/MobEffect": "net/minecraft/class_1291",
    "net/minecraft/world/inventory/MenuType": "net/minecraft/class_3917",
    "net/minecraft/world/item/crafting/RecipeSerializer": "net/minecraft/class_1865",
    "net/minecraft/world/level/levelgen/feature/Feature": "net/minecraft/class_3031",
    "net/minecraft/world/level/levelgen/structure/StructureType": "net/minecraft/class_7151",
    "net/minecraft/client/renderer/entity/EntityRenderer": "net/minecraft/class_897",
    "net/minecraft/client/renderer/blockentity/BlockEntityRenderer": "net/minecraft/class_827",
}
TO_MOJANG = {v: k for k, v in INTERMEDIARY.items()}

def short(name):
    return name.rsplit("/", 1)[-1]

# Base classes whose subclasses mean "custom behaviour in code".
BEHAVIOUR_BASES = {
    "net/minecraft/world/item/Item": "item",
    "net/minecraft/world/item/BlockItem": "item",
    "net/minecraft/world/level/block/Block": "block",
    "net/minecraft/world/entity/Entity": "entity",
    "net/minecraft/world/entity/LivingEntity": "entity",
    "net/minecraft/world/entity/Mob": "entity",
    "net/minecraft/world/level/block/entity/BlockEntity": "block entity",
    "net/minecraft/world/effect/MobEffect": "effect",
    "net/minecraft/world/level/levelgen/feature/Feature": "worldgen feature (code)",
    "net/minecraft/client/renderer/entity/EntityRenderer": "entity renderer",
    "net/minecraft/client/renderer/blockentity/BlockEntityRenderer": "block entity renderer",
}

REGISTRATION_APIS = {
    "net/minecraft/core/Registry": "Registry.register (vanilla / Fabric style)",
    "net/neoforged/neoforge/registries/DeferredRegister": "NeoForge DeferredRegister",
    "net/neoforged/neoforge/registries/RegisterEvent": "NeoForge RegisterEvent",
    "net/minecraftforge/registries/DeferredRegister": "Forge DeferredRegister",
    "net/minecraftforge/registries/RegisterEvent": "Forge RegisterEvent",
}

# Loader API area -> what a Conductor port uses instead. Keys are prefixes of a class name.
API_HINTS = [
    ("net/fabricmc/fabric/api/itemgroup", "creative tabs: `.tab(\"minecraft:...\")` on the declaration"),
    ("net/fabricmc/fabric/api/object/builder", "item/block settings: ContentDeclarations; entity types: not yet (phase 2)"),
    ("net/fabricmc/fabric/api/registry", "flammable/fuel/compost/strippable registries: not yet; data tags where vanilla has them"),
    ("net/fabricmc/fabric/api/resource", "automatic: Conductor loads the module jar's assets/ and data/ as packs"),
    ("net/fabricmc/fabric/api/datagen", "build-time only: run datagen once and ship the generated JSON"),
    ("net/fabricmc/fabric/api/biome", "biome modification: not yet (vanilla data can override whole biomes)"),
    ("net/fabricmc/fabric/api/client/rendering", "renderers/colour providers: native module, or overlays (showOverlay) for simple drawing"),
    ("net/fabricmc/fabric/api/blockrenderlayer", "render layers: not yet"),
    ("net/fabricmc/fabric/api/client/keybinding", "keybinds: not yet (commands: ModuleContext.command)"),
    ("net/fabricmc/fabric/api/client/command", "client commands: ModuleContext.command (+prefix)"),
    ("net/fabricmc/fabric/api/command", "server commands: not yet"),
    ("net/fabricmc/fabric/api/event/lifecycle", "tick events: ConductorModule.onTick"),
    ("net/fabricmc/fabric/api/client/event/lifecycle", "client tick: ConductorModule.onTick"),
    ("net/fabricmc/fabric/api/networking", "networking: not yet"),
    ("net/fabricmc/fabric/api/client/networking", "networking: not yet"),
    ("net/fabricmc/fabric/api/event/player", "player interaction events: not yet"),
    ("net/fabricmc/fabric/api/loot", "loot table modification: not yet (replace the table in data/)"),
    ("net/fabricmc/fabric/api/entity", "entity attributes/attachments: phase 2"),
    ("net/fabricmc/fabric/api/attachment", "data attachments: not yet"),
    ("net/fabricmc/fabric/api/transfer", "item/fluid transfer: not yet"),
    ("net/fabricmc/fabric/api/screenhandler", "menus/GUIs: not yet"),
    ("net/fabricmc/fabric/api/message", "chat: Topics.CHAT, sendChat, showMessage"),
    ("net/fabricmc/fabric/api/client/rendering/v1/hud", "HUD: not yet"),
    ("net/fabricmc/fabric/api/client/rendering/v1/world", "world rendering: showOverlay (boxes, lines, tracers)"),
    ("net/neoforged/neoforge/registries", "registration: ContentDeclarations (items, blocks); others not yet"),
    ("net/neoforged/neoforge/event", "events: see Conductor topics and onTick; most are not yet available"),
    ("net/neoforged/neoforge/client", "client hooks: native module, or Conductor's overlays/commands where they fit"),
    ("net/neoforged/neoforge/network", "networking: not yet"),
    ("net/neoforged/neoforge/common/data", "datagen: build-time only; ship the generated JSON"),
    ("net/minecraftforge/registries", "registration: ContentDeclarations (items, blocks); others not yet"),
    ("net/minecraftforge/event", "events: see Conductor topics and onTick; most are not yet available"),
    ("net/minecraftforge/client", "client hooks: native module, or Conductor's overlays/commands where they fit"),
    ("net/minecraftforge/network", "networking: not yet"),
    ("org/quiltmc/qsl", "Quilt standard libraries: as for the matching Fabric API area"),
]
LOADER_PREFIXES = {"net/fabricmc/fabric/api/": "Fabric API", "net/fabricmc/loader/": "Fabric Loader",
                   "net/neoforged/": "NeoForge", "net/minecraftforge/": "Forge", "org/quiltmc/": "Quilt"}

LOADER_ONLY_KEYS = ("fabric:load_conditions", "neoforge:conditions", "forge:conditions", "conditions")
LOADER_ONLY_TYPE = re.compile(r'"type"\s*:\s*"(neoforge|forge|fabric):')
LOADER_ONLY_DIRS = re.compile(r"^data/[^/]+/(data_maps|neoforge|forge|fabric)/")

# ---------------------------------------------------------------- class files

def class_info(data):
    """(this, super, interfaces, referenced class names) from a class file's constant pool."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    count = struct.unpack(">H", data[8:10])[0]
    pos, i = 10, 1
    utf8, classes = {}, {}
    while i < count:
        tag = data[pos]
        if tag == 1:
            n = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            utf8[i] = data[pos + 3:pos + 3 + n].decode("utf-8", "replace")
            pos += 3 + n
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            if tag == 7:
                classes[i] = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            pos += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag == 15:
            pos += 4
        else:
            raise ValueError(f"unknown constant tag {tag}")
        i += 1
    access, this_i, super_i = struct.unpack(">HHH", data[pos:pos + 6])
    n_if = struct.unpack(">H", data[pos + 6:pos + 8])[0]
    ifaces = [utf8[classes[struct.unpack(">H", data[pos + 8 + 2 * k:pos + 10 + 2 * k])[0]]] for k in range(n_if)]
    refs = {utf8[v] for v in classes.values()}
    for s in utf8.values():  # descriptors and annotation values: Lpkg/Name;
        refs.update(re.findall(r"L([A-Za-z_$][\w/$]*);", s))
    this = utf8[classes[this_i]]
    sup = utf8[classes[super_i]] if super_i else None
    return this, sup, ifaces, refs

def mojang(name):
    return TO_MOJANG.get(name, name)

# ---------------------------------------------------------------- metadata

METADATA_FILES = (("fabric.mod.json", "Fabric"), ("quilt.mod.json", "Quilt"), ("META-INF/neoforge.mods.toml", "NeoForge"),
                  ("META-INF/mods.toml", "Forge"))

def read_metadata(z):
    names = set(z.namelist())
    meta = {"loader": "unknown", "id": None, "version": None, "name": None, "minecraft": None, "mixin_configs": []}
    meta["loaders"] = [label for path, label in METADATA_FILES if path in names] or ["unknown"]
    if "fabric.mod.json" in names:
        d = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"))
        meta.update(loader="Fabric", id=d.get("id"), version=d.get("version"), name=d.get("name"),
                    minecraft=(d.get("depends") or {}).get("minecraft"),
                    entrypoints=sorted((d.get("entrypoints") or {}).keys()))
        meta["mixin_configs"] = [m if isinstance(m, str) else m.get("config") for m in d.get("mixins", [])]
    elif "quilt.mod.json" in names:
        d = json.loads(z.read("quilt.mod.json").decode("utf-8", "replace"))
        ql = d.get("quilt_loader", {})
        meta.update(loader="Quilt", id=ql.get("id"), version=ql.get("version"), name=(ql.get("metadata") or {}).get("name"))
        mx = d.get("mixin", [])
        meta["mixin_configs"] = [mx] if isinstance(mx, str) else list(mx)
    else:
        for path, loader in (("META-INF/neoforge.mods.toml", "NeoForge"), ("META-INF/mods.toml", "Forge")):
            if path in names:
                text = z.read(path).decode("utf-8", "replace")
                meta["loader"] = loader
                d = {}
                if tomllib:
                    try:
                        d = tomllib.loads(text)
                    except Exception:
                        d = {}
                mods = d.get("mods") or [{}]
                meta.update(id=mods[0].get("modId"), version=mods[0].get("version"), name=mods[0].get("displayName"))
                deps = d.get("dependencies", {})
                for dep in (deps.get(meta["id"]) or []) if isinstance(deps, dict) else []:
                    if dep.get("modId") == "minecraft":
                        meta["minecraft"] = dep.get("versionRange")
                mixins = d.get("mixins") or []
                meta["mixin_configs"] = [m.get("config") for m in mixins if isinstance(m, dict)]
                if not meta["mixin_configs"]:
                    meta["mixin_configs"] = sorted(n for n in names if n.endswith(".mixins.json") and "/" not in n)
                break
    meta["mixin_configs"] = [c for c in meta["mixin_configs"] if c]
    return meta

# ---------------------------------------------------------------- analysis

def analyse(jar_path):
    z = zipfile.ZipFile(jar_path)
    names = z.namelist()
    meta = read_metadata(z)
    r = {"jar": os.path.basename(jar_path), "meta": meta}

    # assets and data
    assets = [n for n in names if n.startswith("assets/") and not n.endswith("/")]
    data = [n for n in names if n.startswith("data/") and not n.endswith("/")]
    namespaces = sorted({n.split("/")[1] for n in assets + data if n.count("/") >= 2} - {"minecraft", "c", "fabric", "forge", "neoforge"})
    items, blocks, entities, lang, shaped = set(), set(), set(), {}, set()
    for n in assets:
        m = re.match(r"assets/([^/]+)/(items|models/item)/(.+)\.json$", n)
        if m:
            items.add(f"{m.group(1)}:{m.group(3)}")
        m = re.match(r"assets/([^/]+)/blockstates/(.+)\.json$", n)
        if m:
            bid = f"{m.group(1)}:{m.group(2)}"
            blocks.add(bid)
            try:
                bs = json.loads(z.read(n).decode("utf-8", "replace"))
                variants = bs.get("variants")
                if "multipart" in bs or (isinstance(variants, dict) and set(variants) - {""}):
                    shaped.add(bid)
            except (ValueError, AttributeError):
                shaped.add(bid)
        m = re.match(r"assets/([^/]+)/lang/en_us\.json$", n)
        if m:
            try:
                lang.update(json.loads(z.read(n).decode("utf-8", "replace")))
            except ValueError:
                pass
    for key in lang:
        m = re.match(r"(item|block|entity)\.([a-z0-9_.-]+)\.([a-z0-9_./-]+)$", key)
        if m and m.group(2) in namespaces:
            {"item": items, "block": blocks, "entity": entities}[m.group(1)].add(f"{m.group(2)}:{m.group(3)}")
    items = {i for i in items if i.split(":")[0] in namespaces}
    blocks = {b for b in blocks if b.split(":")[0] in namespaces}
    block_items = items & blocks
    spawn_eggs = {i for i in items if i.endswith("_spawn_egg")}
    data_kinds = {}
    for n in data:
        parts = n.split("/")
        if len(parts) >= 4:
            kind = parts[2] if parts[2] != "worldgen" else "worldgen/" + parts[3]
            data_kinds[kind] = data_kinds.get(kind, 0) + 1
    old_layout = sorted(k for k in data_kinds if k in ("recipes", "loot_tables", "advancements", "structures", "functions",
                                                       "predicates", "item_modifiers") or k == "tags" and any(
        re.match(r"data/[^/]+/tags/(items|blocks|entity_types|fluids)/", n) for n in data))
    loader_only = []
    for n in data:
        if LOADER_ONLY_DIRS.match(n):
            loader_only.append((n, "loader-only folder"))
            continue
        if n.endswith(".json"):
            text = z.read(n).decode("utf-8", "replace")
            if any(f'"{k}"' in text for k in LOADER_ONLY_KEYS[:3]) or LOADER_ONLY_TYPE.search(text):
                loader_only.append((n, "loader-specific JSON (conditions or a loader type)"))
    shaped &= blocks
    r.update(namespaces=namespaces, items=sorted(items - block_items), blocks=sorted(blocks), block_items=sorted(block_items),
             shaped_blocks=sorted(shaped),
             spawn_eggs=sorted(spawn_eggs), entities=sorted(entities), data_kinds=dict(sorted(data_kinds.items())),
             old_data_layout=old_layout, loader_only=loader_only, assets=len(assets), data=len(data))

    # classes
    infos, problems = {}, []
    for n in names:
        if n.endswith(".class") and not n.startswith("META-INF/versions/"):
            try:
                this, sup, ifaces, refs = class_info(z.read(n))
                infos[this] = (mojang(sup) if sup else None, [mojang(i) for i in ifaces], {mojang(x) for x in refs})
            except Exception as e:  # noqa: BLE001 - report and carry on
                problems.append(f"{n}: {e}")
    def base_of(cls, seen=()):
        sup = infos.get(cls, (None,))[0]
        while sup and sup not in BEHAVIOUR_BASES and sup in infos and sup not in seen:
            seen = seen + (sup,)
            sup = infos[sup][0]
        return BEHAVIOUR_BASES.get(sup)
    behaviour = {}
    for cls in infos:
        kind = base_of(cls)
        if kind:
            behaviour.setdefault(kind, []).append(cls.replace("/", "."))
    all_refs = set().union(*(v[2] for v in infos.values())) if infos else set()
    registration = sorted({label for cls, label in REGISTRATION_APIS.items() if cls in all_refs})
    loader_apis = {}
    for ref in all_refs:
        for prefix, label in LOADER_PREFIXES.items():
            if ref.startswith(prefix):
                area = ref[len(prefix):].rsplit("/", 1)[0] if "/" in ref[len(prefix):] else ""
                if label == "Fabric API":
                    area = "/".join(area.split("/")[:2])
                else:
                    area = "/".join(area.split("/")[:2])
                loader_apis.setdefault(label, set()).add(area or "(root)")
    hints = []
    for prefix, hint in API_HINTS:
        if any(ref.startswith(prefix) for ref in all_refs):
            hints.append((prefix, hint))
    mixins = []
    for cfg in meta["mixin_configs"]:
        try:
            d = json.loads(z.read(cfg).decode("utf-8", "replace"))
            pkg = d.get("package", "")
            for side in ("mixins", "client", "server"):
                for m in d.get(side) or []:
                    mixins.append((side, f"{pkg}.{m}" if pkg else m))
        except KeyError:
            problems.append(f"mixin config {cfg} not in the jar")
        except ValueError as e:
            problems.append(f"mixin config {cfg}: {e}")
    mixin_targets = set()
    for side, m in mixins:
        info = infos.get(m.replace(".", "/"))
        if info:
            mixin_targets.update(x for x in info[2] if x.startswith("net/minecraft/") and x != m.replace(".", "/"))
    r.update(classes=len(infos), behaviour={k: sorted(v) for k, v in sorted(behaviour.items())}, registration=registration,
             loader_apis={k: sorted(v) for k, v in sorted(loader_apis.items())}, hints=hints, mixins=mixins,
             class_problems=problems)

    # verdict
    code_kinds = {k: len(v) for k, v in r["behaviour"].items()}
    declarable_items = len(r["items"]) - len(r["spawn_eggs"])
    r["verdict"] = verdict(r, code_kinds, declarable_items)
    return r

def verdict(r, code_kinds, declarable_items):
    reasons = []
    if r["mixins"]:
        reasons.append(f"{len(r['mixins'])} Mixin(s) into Minecraft: native module (tied to one Minecraft version)")
    for kind, n in code_kinds.items():
        reasons.append(f"{n} class(es) with custom {kind} behaviour: needs code Conductor can't declare yet")
    if r["entities"]:
        reasons.append(f"{len(r['entities'])} entit(y/ies): mobs are phase 2")
    if r["shaped_blocks"]:
        reasons.append(f"{len(r['shaped_blocks'])} block(s) with block-state properties (stairs, slabs, rotation, ...): "
                       "declarations cover full cubes only so far")
    if not reasons:
        level = "READY: everything found is declarable or data"
    elif not r["mixins"] and set(code_kinds) <= {"item", "block"} and not r["entities"] and len(r["shaped_blocks"]) < len(r["blocks"]) + 1:
        level = "PARTIAL: data and simple items/blocks port; custom item/block behaviour stays behind"
    else:
        level = "NATIVE: the code needs a native (version-specific) module; data and simple content still port"
    return {"level": level, "reasons": reasons, "declarable_items": declarable_items,
            "declarable_blocks": len(r["blocks"]) - len(r["shaped_blocks"])}

# ---------------------------------------------------------------- report

def report(r):
    m = r["meta"]
    out = [f"# Conductor readiness report: {m.get('name') or m.get('id') or r['jar']}", ""]
    out.append(f"- Jar: `{r['jar']}`")
    out.append(f"- Loader metadata: **{', '.join(m['loaders'])}** (details read from {m['loader']}), mod id `{m.get('id')}`, "
               f"version `{m.get('version')}`, Minecraft `{m.get('minecraft')}`")
    out.append(f"- Namespaces: {', '.join(r['namespaces']) or '(none)'}")
    out.append(f"- **Verdict: {r['verdict']['level']}**")
    for reason in r["verdict"]["reasons"]:
        out.append(f"  - {reason}")
    out += ["", "## Content found (from assets and names)", "",
            "| Kind | Count | Conductor |", "|---|---|---|",
            f"| Items | {len(r['items'])} | declarable (`content.item`); check stack size, durability, food and rarity against the original |",
            f"| Blocks, full cubes | {len(r['blocks']) - len(r['shaped_blocks'])} | declarable (`content.block`); check strength, sound and light |",
            f"| Blocks with states (stairs, slabs, rotation, ...) | {len(r['shaped_blocks'])} | not yet: declarations cover full cubes only |",
            f"| Block items | {len(r['block_items'])} | made with their block |",
            f"| Spawn eggs | {len(r['spawn_eggs'])} | phase 2 (with the mobs) |",
            f"| Entities | {len(r['entities'])} | phase 2 |",
            f"| Asset files | {r['assets']} | copied: Conductor loads them as a resource pack |",
            f"| Data files | {r['data']} | copied: Conductor loads them as a data pack |", ""]
    if r["data_kinds"]:
        out.append("Data by kind: " + ", ".join(f"{k} {v}" for k, v in r["data_kinds"].items()))
        out.append("")
    if r["old_data_layout"]:
        out.append(f"**Pre-1.21 data layout** ({', '.join(r['old_data_layout'])}): 1.21 renamed these folders to the singular "
                   "(`recipe/`, `loot_table/`, `tags/item/`, ...). Rename them, or the game ignores the files.")
        out.append("")
    if r["loader_only"]:
        out += ["## Loader-only data (not copied)", ""]
        out += [f"- `{n}`: {why}" for n, why in r["loader_only"][:50]]
        if len(r["loader_only"]) > 50:
            out.append(f"- ... and {len(r['loader_only']) - 50} more")
        out.append("")
    out += ["## Code", "", f"- Classes: {r['classes']}"]
    if r["registration"]:
        out.append(f"- Registration through: {', '.join(r['registration'])}")
    for kind, classes in r["behaviour"].items():
        listed = ", ".join(c.rsplit(".", 1)[-1] for c in classes[:8]) + (" ..." if len(classes) > 8 else "")
        out.append(f"- Custom {kind} classes ({len(classes)}): {listed}")
    if r["mixins"]:
        out.append(f"- Mixins ({len(r['mixins'])}): " + ", ".join(f"{m.rsplit('.', 1)[-1]} ({side})" for side, m in r["mixins"][:12])
                   + (" ..." if len(r["mixins"]) > 12 else ""))
    for loader, areas in r["loader_apis"].items():
        out.append(f"- {loader} APIs used: {', '.join(areas)}")
    if r["hints"]:
        out += ["", "## What to use instead", ""]
        out += [f"- `{p}`: {h}" for p, h in r["hints"]]
    if r["class_problems"]:
        out += ["", "## Classes that could not be read", ""] + [f"- {p}" for p in r["class_problems"][:20]]
    sk = r.get("skeleton")
    if sk:
        out += ["", "## What the skeleton changed", "",
                f"- {sk['copied']} asset and data files copied; {sk['item_definitions']} item definitions added for 1.21.4+.",
                f"- {sk['recipes_migrated']} recipe(s) moved to the 1.21.2+ format (ingredients as `\"id\"`/`\"#tag\"`, results as `id`).",
                f"- {sk['tag_entries_optional']} tag entr(y/ies) for content not ported made optional (`\"required\": false`), "
                "so the tag, and vanilla tags that include it, still load."]
        if sk["left_out"]:
            kinds = {}
            for n, _ in sk["left_out"]:
                k = n.split("/")[2]
                kinds[k] = kinds.get(k, 0) + 1
            out.append(f"- {len(sk['left_out'])} data file(s) left out because they need content not ported yet: "
                       + ", ".join(f"{k} {v}" for k, v in sorted(kinds.items())) + ". For example:")
            out += [f"  - `{n}` needs {', '.join(needs[:3])}" for n, needs in sk["left_out"][:8]]
    out += ["", "Generated by `tools/convert/conductor_convert.py`. Counts come from the jar's files and class constant pools: a "
            "static reading, not a run of the mod. Custom behaviour found in code is real; content found only in assets may "
            "miss items that have no model file."]
    return "\n".join(out) + "\n"

# ---------------------------------------------------------------- skeleton

def java_ident(s):
    s = re.sub(r"[^A-Za-z0-9]", " ", s).title().replace(" ", "")
    return ("M" + s) if not s or s[0].isdigit() else s

# Recipe keys that hold ingredients. Before 1.21.2 an ingredient was {"item": id} or {"tag": id}; since then it is "id" or "#id".
INGREDIENT_KEYS = ("ingredient", "ingredients", "key", "base", "addition", "template")

def migrate_ingredient(v):
    if isinstance(v, list):
        return [migrate_ingredient(x) for x in v]
    if isinstance(v, dict):
        if set(v) == {"item"}:
            return v["item"]
        if set(v) == {"tag"}:
            return "#" + v["tag"]
    return v

def migrate_recipe(d):
    """1.21.2+ recipe format; returns (recipe, changed)."""
    before = json.dumps(d, sort_keys=True)
    for k in INGREDIENT_KEYS:
        if k in d:
            d[k] = {kk: migrate_ingredient(vv) for kk, vv in d[k].items()} if k == "key" and isinstance(d[k], dict) else migrate_ingredient(d[k])
    res = d.get("result")
    if isinstance(res, dict) and "item" in res and "id" not in res:
        res["id"] = res.pop("item")
    elif isinstance(res, str) and d.get("type", "").endswith(("smelting", "blasting", "smoking", "campfire_cooking", "stonecutting")):
        d["result"] = {"id": res, "count": d.pop("count", 1)} if "stonecutting" in d.get("type", "") else {"id": res}
    return d, json.dumps(d, sort_keys=True) != before

def declared_ids(r, ns_main):
    blocks = {b for b in r["blocks"] if b.split(":")[0] == ns_main and b not in r["shaped_blocks"]}
    items = {i for i in r["items"] if i.split(":")[0] == ns_main and i not in r["spawn_eggs"]}
    return blocks, items, blocks | items | {b for b in blocks if b in r["block_items"]}

def write_skeleton(r, jar_path, out_dir):
    z = zipfile.ZipFile(jar_path)
    loader_only = {n for n, _ in r["loader_only"]}
    res = os.path.join(out_dir, "src/main/resources")
    ns_main = r["namespaces"][0] if r["namespaces"] else (r["meta"].get("id") or "converted")
    _, _, declared = declared_ids(r, ns_main)
    known = set(r["items"]) | set(r["blocks"]) | set(r["block_items"])
    missing = known - declared
    stats = {"copied": 0, "recipes_migrated": 0, "tag_entries_optional": 0, "left_out": []}
    id_string = re.compile(r'"#?([a-z0-9_.-]+:[a-z0-9_./-]+)"')
    for n in z.namelist():
        if n.endswith("/") or n in loader_only or not (n.startswith("assets/") or n.startswith("data/")):
            continue
        dest = os.path.join(res, n)
        payload = z.read(n)
        if n.startswith("data/") and n.endswith(".json"):
            try:
                d = json.loads(payload.decode("utf-8"))
            except ValueError:
                d = None
            if d is not None:
                if re.match(r"data/[^/]+/tags/", n) and isinstance(d.get("values"), list):
                    # a tag fails as a whole on one unknown entry, taking vanilla tags that include it down too:
                    # entries for content not ported become optional
                    vals = []
                    for v in d["values"]:
                        vid = v if isinstance(v, str) else v.get("id") if isinstance(v, dict) else None
                        if vid in missing:
                            vals.append({"id": vid, "required": False})
                            stats["tag_entries_optional"] += 1
                        else:
                            vals.append(v)
                    d["values"] = vals
                else:
                    changed = False
                    if re.match(r"data/[^/]+/recipes?/", n) and isinstance(d, dict):
                        d, changed = migrate_recipe(d)
                    needs = sorted({m for m in id_string.findall(json.dumps(d)) if m in missing})
                    if needs:
                        stats["left_out"].append((n, needs))
                        continue
                    stats["recipes_migrated"] += changed
                payload = json.dumps(d, indent=2).encode("utf-8")
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as f:
            f.write(payload)
        stats["copied"] += 1
    # 1.21.4+ shows an item only through assets/<ns>/items/<id>.json. Older mods have just models/item/<id>.json:
    # add the item definition pointing at that model (an existing definition is never replaced).
    generated = 0
    for n in z.namelist():
        m = re.match(r"assets/([^/]+)/models/item/([^/]+)\.json$", n)
        if m and f"{m.group(1)}:{m.group(2)}" in declared:
            dest = os.path.join(res, "assets", m.group(1), "items", m.group(2) + ".json")
            if not os.path.exists(dest):
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                with open(dest, "w") as f:
                    json.dump({"model": {"type": "minecraft:model", "model": f"{m.group(1)}:item/{m.group(2)}"}}, f)
                generated += 1
    stats["item_definitions"] = generated
    r["skeleton"] = stats
    cls = java_ident(ns_main) + "Content"
    pkg = "conductor.converted." + re.sub(r"[^a-z0-9_]", "_", ns_main.lower())
    src_dir = os.path.join(out_dir, "src/main/java", *pkg.split("."))
    os.makedirs(src_dir, exist_ok=True)
    lines, skipped = [], []
    custom = {c.rsplit(".", 1)[-1].lower() for k in ("item", "block") for c in r["behaviour"].get(k, [])}
    for b in r["blocks"]:
        ns, path = b.split(":", 1)
        if ns != ns_main:
            continue
        if b in r["shaped_blocks"]:
            skipped.append(f"        // {path}: has block-state properties (stairs, slab, rotation, ...); not declarable yet")
            continue
        note = "  // TODO: a class named like this has custom code in the original" if any(path.replace("_", "") in c for c in custom) else ""
        item = "" if b in r["block_items"] else ".noItem()"
        lines.append(f'        content.block("{path}"){item}; // TODO: strength, sound, light, requiresTool{note}')
    for i in r["items"]:
        ns, path = i.split(":", 1)
        if ns != ns_main or i in r["spawn_eggs"]:
            continue
        note = "  // TODO: custom code in the original" if any(path.replace("_", "") in c for c in custom) else ""
        lines.append(f'        content.item("{path}"); // TODO: maxStack, durability, food, rarity, tab{note}')
    others = [n for n in r["namespaces"] if n != ns_main]
    java = f"""package {pkg};

import conductor.contract.ContentDeclarations;
import conductor.contract.ContentModule;

/**
 * Converted from {r['jar']} ({r['meta']['loader']}) by conductor-convert. Ids and assets are the original's; properties
 * are defaults until the TODOs are filled in. See README.md for what could not be converted.
 */
public final class {cls} implements ContentModule {{

    @Override
    public String namespace() {{
        return "{ns_main}";
    }}

    @Override
    public void declare(ContentDeclarations content) {{
{chr(10).join(lines) if lines else '        // nothing declarable was found: this module is data and assets only'}{(chr(10) + chr(10).join(skipped)) if skipped else ''}
    }}
}}
"""
    if others:
        java = java.replace("public final class", f"// Other namespaces in the jar ({', '.join(others)}) need a ContentModule each.\npublic final class", 1)
    with open(os.path.join(src_dir, cls + ".java"), "w") as f:
        f.write(java)
    svc = os.path.join(res, "META-INF/services")
    os.makedirs(svc, exist_ok=True)
    with open(os.path.join(svc, "conductor.contract.ContentModule"), "w") as f:
        f.write(f"{pkg}.{cls}\n")
    with open(os.path.join(out_dir, "build.gradle"), "w") as f:
        f.write("// Converted content module: compile against the Conductor contract only.\n"
                "dependencies {\n    compileOnly project(':contract')\n}\n")
    with open(os.path.join(out_dir, "README.md"), "w") as f:
        f.write(report(r))
    return {"copied": stats["copied"], "class": f"{pkg}.{cls}", "declared": len(lines), "item_definitions": generated,
            "recipes_migrated": stats["recipes_migrated"], "tag_entries_optional": stats["tag_entries_optional"],
            "left_out": len(stats["left_out"])}

def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("jar")
    p.add_argument("--out", help="write a Conductor content-module skeleton here")
    p.add_argument("--json", action="store_true", help="print the analysis as JSON instead of the report")
    a = p.parse_args(argv)
    r = analyse(a.jar)
    if a.json:
        print(json.dumps(r, indent=2, default=list))
    else:
        sys.stdout.write(report(r))
    if a.out:
        s = write_skeleton(r, a.jar, a.out)
        print(f"\nSkeleton written to {a.out}: {s['copied']} asset/data files, {s['item_definitions']} item definitions "
              f"added for 1.21.4+, {s['declared']} declarations in {s['class']}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
