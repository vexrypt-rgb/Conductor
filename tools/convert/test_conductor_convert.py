"""Tests for conductor_convert. Run: python3 -m unittest discover -s tools/convert   (needs a JDK for javac)."""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(__file__))
import conductor_convert as cc  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CONTRACT_CLASSES = os.path.join(ROOT, "contract", "build", "classes", "java", "main")


def read(path):
    with open(path) as f:
        return f.read()


def javac(src_dir, out_dir, classpath=None):
    files = [os.path.join(d, f) for d, _, fs in os.walk(src_dir) for f in fs if f.endswith(".java")]
    cmd = ["javac", "-d", out_dir] + (["-cp", classpath] if classpath else []) + files
    subprocess.run(cmd, check=True, capture_output=True)


class ConvertTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp()
        src, out = os.path.join(cls.tmp, "src"), os.path.join(cls.tmp, "classes")
        os.makedirs(out)
        sources = {
            # intermediary-named stand-ins for Minecraft's Item, Block and Registry, as a Fabric mod sees them
            "net/minecraft/class_1792.java": "package net.minecraft; public class class_1792 {}",
            "net/minecraft/class_2248.java": "package net.minecraft; public class class_2248 {}",
            "net/minecraft/class_2378.java": "package net.minecraft; public class class_2378 { public static Object register(Object a, Object b) { return b; } }",
            "net/minecraft/class_1297.java": "package net.minecraft; public class class_1297 {}",                       # Entity
            "net/minecraft/class_1642.java": "package net.minecraft; public class class_1642 extends class_1297 {}",   # Zombie
            "com/example/gems/ZombieCatEntity.java": "package com.example.gems; public class ZombieCatEntity extends net.minecraft.class_1642 {}",
            "com/example/gems/WandItem.java": "package com.example.gems; public class WandItem extends net.minecraft.class_1792 {}",
            "com/example/gems/MagicWandItem.java": "package com.example.gems; public class MagicWandItem extends WandItem {}",
            "com/example/gems/GemInit.java": "package com.example.gems; public class GemInit { static Object x = net.minecraft.class_2378.register(null, new net.minecraft.class_1792()); }",
            # a block class that only sets what a MODEL declaration covers: its outline (method_9530) and mixed sounds
            "com/example/gems/GemLampBlock.java": "package com.example.gems; public class GemLampBlock extends net.minecraft.class_2248 { "
                                                  "static final String[] S = {\"block.lantern.break\", \"block.glass.step\", \"block.lantern.place\", "
                                                  "\"block.lantern.hit\", \"block.glass.fall\"}; protected Object method_9530() { return null; } }",
            "org/spongepowered/asm/mixin/Mixin.java": "package org.spongepowered.asm.mixin; public @interface Mixin { Class<?>[] value() default {}; String[] targets() default {}; }",
            "com/example/gems/mixin/TitleMixin.java": "package com.example.gems.mixin; @org.spongepowered.asm.mixin.Mixin(value = net.minecraft.class_2248.class, targets = \"net.minecraft.class_1792\") public class TitleMixin { net.minecraft.class_2378 unrelated; }",
        }
        for path, text in sources.items():
            p = os.path.join(src, path)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "w") as f:
                f.write(text)
        javac(src, out)
        cls.jar = os.path.join(cls.tmp, "gems-1.0.jar")
        with zipfile.ZipFile(cls.jar, "w") as z:
            for d, _, fs in os.walk(out):
                for f in fs:
                    full = os.path.join(d, f)
                    rel = os.path.relpath(full, out)
                    if not rel.startswith("net/") and not rel.startswith("org/"):
                        z.write(full, rel)
            z.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "gems", "version": "1.0", "name": "Gems",
                                                      "depends": {"minecraft": "~1.21.4"}, "mixins": ["gems.mixins.json"],
                                                      "entrypoints": {"main": ["com.example.gems.GemInit"]}}))
            z.writestr("gems.mixins.json", json.dumps({"package": "com.example.gems.mixin", "mixins": ["TitleMixin"]}))
            z.writestr("assets/gems/items/ruby.json", "{}")
            z.writestr("assets/gems/items/magic_wand.json", "{}")
            z.writestr("assets/gems/items/gem_block.json", "{}")
            z.writestr("assets/gems/items/zombie_cat_spawn_egg.json", "{}")
            z.writestr("assets/gems/blockstates/gem_block.json", json.dumps({"variants": {"": {"model": "gems:block/gem_block"}}}))
            z.writestr("assets/gems/blockstates/gem_stairs.json", json.dumps({"variants": {"facing=east,half=bottom": {}}}))  # unknown states
            z.writestr("assets/gems/blockstates/gem_block_stairs.json", json.dumps({"variants": {
                "facing=east,half=bottom,shape=straight": {}, "facing=west,half=top,shape=inner_left": {}}}))
            z.writestr("assets/gems/blockstates/gem_slab.json", json.dumps({"variants": {"type=bottom": {}, "type=double": {}, "type=top": {}}}))
            z.writestr("assets/gems/blockstates/gem_wall.json", json.dumps({"multipart": [
                {"when": {"up": "true"}, "apply": {}}, {"when": {"north": "low|tall"}, "apply": {}}]}))
            z.writestr("assets/gems/blockstates/iron_gem_door.json", json.dumps({"variants": {
                "facing=east,half=lower,hinge=left,open=false": {}}}))
            z.writestr("assets/gems/blockstates/gem_observer.json", json.dumps({"variants": {
                f"facing={d}": {"model": "gems:block/gem_observer"} for d in ("down", "up", "north", "south", "west", "east")}}))
            z.writestr("assets/gems/blockstates/gem_furnace.json", json.dumps({"variants": {
                f"facing={d}": {"model": "gems:block/gem_furnace"} for d in ("north", "south", "west", "east")}}))
            z.writestr("assets/gems/models/block/gem_furnace.json", json.dumps({"parent": "minecraft:block/orientable"}))
            # the north variant is the model turned 90 degrees; one element is repeated, one reaches outside the block
            z.writestr("assets/gems/blockstates/gem_lamp.json", json.dumps({"variants": {
                "facing=north": {"model": "gems:block/gem_lamp", "y": 90}, "facing=east": {"model": "gems:block/gem_lamp", "y": 180},
                "facing=south": {"model": "gems:block/gem_lamp", "y": 270}, "facing=west": {"model": "gems:block/gem_lamp"}}}))
            z.writestr("assets/gems/models/block/gem_lamp.json", json.dumps({"parent": "gems:block/lamp_base"}))
            z.writestr("assets/gems/models/block/lamp_base.json", json.dumps({"elements": [
                {"from": [2, 0, 0], "to": [6, 4, 3]}, {"from": [2, 0, 0], "to": [6, 4, 3]}, {"from": [7, 4, 7], "to": [9, 20, 9]}]}))
            z.writestr("assets/gems/lang/en_us.json", json.dumps({"item.gems.ruby": "Ruby", "entity.gems.zombie_cat": "Zombie Cat"}))
            z.writestr("assets/gems/textures/item/ruby.png", b"\x89PNG")
            z.writestr("assets/gems/models/item/old_style.json", "{}")   # a pre-1.21.4 item: model only
            z.writestr("assets/gems/models/item/ruby.json", "{}")
            z.writestr("data/gems/recipe/gem_block.json", json.dumps({   # 1.21.1 format
                "type": "minecraft:crafting_shaped", "pattern": ["##", "##"], "key": {"#": {"item": "gems:ruby"}},
                "result": {"item": "gems:gem_block", "count": 1}}))
            z.writestr("data/gems/recipe/gem_stairs.json", json.dumps({
                "type": "minecraft:crafting_shaped", "pattern": ["#  "], "key": {"#": {"tag": "gems:blocks"}},
                "result": {"id": "gems:gem_stairs", "count": 4}}))
            z.writestr("data/minecraft/tags/block/stairs.json", json.dumps({"values": ["gems:gem_stairs", "minecraft:oak_stairs"]}))
            z.writestr("data/minecraft/tags/block/mineable/pickaxe.json", json.dumps({"values": ["gems:gem_block"]}))
            z.writestr("data/gems/loot_table/blocks/gem_block.json", "{}")
            z.writestr("data/gems/recipe/conditional.json", json.dumps({"fabric:load_conditions": [{}], "type": "minecraft:crafting_shaped"}))
            z.writestr("data/gems/data_maps/item/furnace_fuels.json", "{}")
            z.writestr("data/gems/worldgen/structure/tower.json", "{}")
        cls.r = cc.analyse(cls.jar)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def test_metadata(self):
        m = self.r["meta"]
        self.assertEqual(("Fabric", "gems", "1.0", "~1.21.4"), (m["loader"], m["id"], m["version"], m["minecraft"]))
        self.assertEqual(["Fabric"], m["loaders"])

    def test_content_from_assets_and_names(self):
        self.assertEqual(["gems:magic_wand", "gems:old_style", "gems:ruby", "gems:zombie_cat_spawn_egg"], self.r["items"])
        self.assertEqual(["gems:gem_block", "gems:gem_block_stairs", "gems:gem_furnace", "gems:gem_lamp", "gems:gem_observer",
                          "gems:gem_slab", "gems:gem_stairs", "gems:gem_wall", "gems:iron_gem_door"], self.r["blocks"])
        self.assertEqual(["gems:gem_stairs"], self.r["shaped_blocks"])
        self.assertEqual({"gems:gem_block": "CUBE", "gems:gem_block_stairs": "STAIRS", "gems:gem_slab": "SLAB",
                          "gems:gem_wall": "WALL", "gems:iron_gem_door": "DOOR", "gems:gem_observer": "FACING",
                          "gems:gem_furnace": "HORIZONTAL_FACING", "gems:gem_lamp": "MODEL"}, self.r["block_shapes"])
        self.assertEqual(["gems:gem_block"], self.r["block_items"])
        self.assertEqual(["gems:zombie_cat"], self.r["entities"])
        self.assertEqual(["gems:zombie_cat_spawn_egg"], self.r["spawn_eggs"])
        self.assertEqual({"loot_table": 1, "recipe": 3, "data_maps": 1, "worldgen/structure": 1, "tags": 2}, self.r["data_kinds"])

    def test_model_blocks_take_their_outline_from_the_model_and_their_sounds_from_the_class(self):
        # turned by the north variant's y=90, the repeat dropped, the tall element cut at the block's top
        self.assertEqual({"gems:gem_lamp": [(13.0, 0.0, 2.0, 16.0, 4.0, 6.0), (7.0, 4.0, 7.0, 9.0, 16.0, 9.0)]}, self.r["block_boxes"])
        self.assertEqual({"com.example.gems.GemLampBlock": "gems:gem_lamp"}, self.r["covered_classes"])
        self.assertNotIn("block", self.r["behaviour"], "a covered class is not custom behaviour")
        self.assertEqual({"gems:gem_lamp": ["minecraft:block.lantern.break", "minecraft:block.glass.step", "minecraft:block.lantern.place",
                                            "minecraft:block.lantern.hit", "minecraft:block.glass.fall"]}, self.r["block_sounds"])
        self.assertIn("Block classes a declaration covers (1): GemLampBlock", cc.report(self.r))

    def test_loader_only_data_is_found(self):
        self.assertEqual({"data/gems/recipe/conditional.json", "data/gems/data_maps/item/furnace_fuels.json"},
                         {n for n, _ in self.r["loader_only"]})

    def test_code_through_intermediary_names(self):
        # a subclass of a subclass of Item is still custom item behaviour
        self.assertEqual(["com.example.gems.MagicWandItem", "com.example.gems.WandItem"], self.r["behaviour"]["item"])
        self.assertIn("Registry.register (vanilla / Fabric style)", self.r["registration"])
        self.assertEqual([("mixins", "com.example.gems.mixin.TitleMixin")], self.r["mixins"])
        # the @Mixin annotation's targets (class and string forms), not every class the mixin mentions
        self.assertEqual({"net/minecraft/world/level/block/Block": 1, "net/minecraft/world/item/Item": 1}, self.r["mixin_targets"])

    def test_census_counts_targets_per_mod(self):
        text, ranked = cc.census([self.jar, self.jar])
        self.assertEqual({("net/minecraft/world/item/Item", 2), ("net/minecraft/world/level/block/Block", 2)},
                         {(t, len(ids)) for t, ids in ranked})
        self.assertIn("| 2 | `net.minecraft.world.level.block.Block` | 2 | blocks |", text)
        self.assertEqual([], self.r["class_problems"])

    def test_verdict_names_the_blockers(self):
        v = self.r["verdict"]
        self.assertTrue(v["level"].startswith("NATIVE"), v)
        text = " ".join(v["reasons"])
        self.assertEqual({"com.example.gems.ZombieCatEntity": "ZOMBIE"}, self.r["mob_archetypes"])
        for word in ("Mixin", "item behaviour", "match no Conductor block shape"):
            self.assertIn(word, text)
        self.assertIn("Verdict: NATIVE", cc.report(self.r))

    def test_skeleton_compiles_against_the_contract_and_skips_loader_data(self):
        out = os.path.join(self.tmp, "skeleton")
        s = cc.write_skeleton(self.r, self.jar, out)
        res = os.path.join(out, "src/main/resources")
        self.assertTrue(os.path.isfile(os.path.join(res, "assets/gems/textures/item/ruby.png")))
        self.assertTrue(os.path.isfile(os.path.join(res, "data/gems/worldgen/structure/tower.json")))
        self.assertFalse(os.path.exists(os.path.join(res, "data/gems/recipe/conditional.json")))
        self.assertFalse(os.path.exists(os.path.join(res, "data/gems/data_maps")))
        java = read(os.path.join(out, "src/main/java/conductor/converted/gems/GemsContent.java"))
        self.assertIn('content.block("gem_block");', java)
        self.assertIn('content.item("ruby");', java)
        self.assertIn("gem_stairs: its states match no Conductor block shape", java)
        self.assertIn('content.block("gem_block_stairs").stairsOf("gem_block").noItem();', java)
        self.assertIn('content.block("gem_slab").shape(BlockShape.SLAB).noItem();', java)
        self.assertIn('content.block("iron_gem_door").shape(BlockShape.DOOR).material(BlockMaterial.IRON).noItem();', java)
        self.assertLess(java.index('content.block("gem_block")'), java.index('content.block("gem_block_stairs")'), "base first")
        self.assertIn('content.item("magic_wand"); // TODO: maxStack, durability, food, rarity, tab  // TODO: custom code', java)
        self.assertNotIn("spawn_egg\")", java)
        self.assertIn('content.mob("zombie_cat").like(MobArchetype.ZOMBIE);', java)
        self.assertEqual("conductor.converted.gems.GemsContent\n",
                         read(os.path.join(res, "META-INF/services/conductor.contract.ContentModule")))
        self.assertIn('content.block("gem_observer").shape(BlockShape.FACING).noItem();', java)
        self.assertIn('content.block("gem_furnace").shape(BlockShape.HORIZONTAL_FACING).noItem();', java)
        self.assertIn('content.block("gem_lamp")\n                .box(13, 0, 2, 16, 4, 6)\n                .box(7, 4, 7, 9, 16, 9)\n'
                      '                .sounds("minecraft:block.lantern.break", "minecraft:block.glass.step", "minecraft:block.lantern.place", '
                      '"minecraft:block.lantern.hit", "minecraft:block.glass.fall").noItem(); // TODO: strength, light, requiresTool (outline', java)
        self.assertEqual(12, s["declared"])
        # the 1.21.1 recipe moved to the 1.21.2+ format; the stairs recipe needs content not ported, so it is left out
        self.assertEqual({"type": "minecraft:crafting_shaped", "pattern": ["##", "##"], "key": {"#": "gems:ruby"},
                          "result": {"id": "gems:gem_block", "count": 1}},
                         json.loads(read(os.path.join(res, "data/gems/recipe/gem_block.json"))))
        self.assertFalse(os.path.exists(os.path.join(res, "data/gems/recipe/gem_stairs.json")))
        self.assertEqual(1, s["left_out"])
        # a vanilla tag listing content not ported keeps loading: that entry becomes optional
        self.assertEqual({"values": [{"id": "gems:gem_stairs", "required": False}, "minecraft:oak_stairs"]},
                         json.loads(read(os.path.join(res, "data/minecraft/tags/block/stairs.json"))))
        self.assertEqual({"values": ["gems:gem_block"]}, json.loads(read(os.path.join(res, "data/minecraft/tags/block/mineable/pickaxe.json"))))
        self.assertEqual((1, 1), (s["recipes_migrated"], s["tag_entries_optional"]))
        report = read(os.path.join(out, "README.md"))
        self.assertIn("left out because they need content not ported yet: recipe 1", report)
        self.assertEqual(1, s["item_definitions"], "only old_style (declared) lacked a definition")
        self.assertEqual({"model": {"type": "minecraft:model", "model": "gems:item/old_style"}},
                         json.loads(read(os.path.join(res, "assets/gems/items/old_style.json"))))
        self.assertEqual("{}", read(os.path.join(res, "assets/gems/items/ruby.json")), "an existing definition is kept")
        if not os.path.isdir(CONTRACT_CLASSES):
            self.skipTest("build the contract first (./gradlew :contract:compileJava) to compile the skeleton")
        classes = os.path.join(self.tmp, "skeleton-classes")
        os.makedirs(classes)
        javac(os.path.join(out, "src/main/java"), classes, CONTRACT_CLASSES)

    def test_multi_loader_jars_list_every_loader(self):
        jar = os.path.join(self.tmp, "multi.jar")
        with zipfile.ZipFile(jar, "w") as z:
            z.writestr("fabric.mod.json", json.dumps({"id": "multi", "version": "2"}))
            z.writestr("META-INF/neoforge.mods.toml", 'modLoader="javafml"\n[[mods]]\nmodId="multi"\nversion="2"\n')
        self.assertEqual(["Fabric", "NeoForge"], cc.analyse(jar)["meta"]["loaders"])

    def test_neoforge_metadata(self):
        jar = os.path.join(self.tmp, "neo.jar")
        with zipfile.ZipFile(jar, "w") as z:
            z.writestr("META-INF/neoforge.mods.toml", 'modLoader="javafml"\n[[mods]]\nmodId="neo"\nversion="3.1"\n'
                                                       'displayName="Neo Mod"\n[[dependencies.neo]]\nmodId="minecraft"\n'
                                                       'versionRange="[1.21.4,)"\n[[mixins]]\nconfig="neo.mixins.json"\n')
        m = cc.analyse(jar)["meta"]
        if cc.tomllib is None:
            self.skipTest("Python 3.11+ reads mods.toml")
        self.assertEqual(("NeoForge", "neo", "3.1", "Neo Mod", "[1.21.4,)", ["neo.mixins.json"]),
                         (m["loader"], m["id"], m["version"], m["name"], m["minecraft"], m["mixin_configs"]))


if __name__ == "__main__":
    unittest.main()
