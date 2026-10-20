"""Tests for generate_recipes.py on small synthetic jars. Run: python3 -m unittest discover -s scripts/recipes"""

import io
import json
import unittest
import zipfile

import generate_recipes as gen

LANG = {
    "item.minecraft.stick": "Stick",
    "block.minecraft.oak_planks": "Oak Planks",
    "block.minecraft.spruce_planks": "Spruce Planks",
    "block.minecraft.oak_log": "Oak Log",
    "item.minecraft.iron_ingot": "Iron Ingot",
    "item.minecraft.iron_nugget": "Iron Nugget",
    "block.minecraft.iron_block": "Block of Iron",
    "item.minecraft.raw_iron": "Raw Iron",
    "item.minecraft.coal": "Coal",
    "item.minecraft.charcoal": "Charcoal",
    "block.minecraft.torch": "Torch",
    "item.minecraft.bundle": "Bundle",
    "item.minecraft.red_bundle": "Red Bundle",
    "item.minecraft.red_dye": "Red Dye",
    "item.minecraft.skull_banner_pattern": "Banner Pattern",
    "item.minecraft.skull_banner_pattern.desc": "Skull Charge",
    "item.minecraft.flower_banner_pattern": "Banner Pattern",
    "item.minecraft.flower_banner_pattern.desc": "Flower Charge",
    "item.minecraft.paper": "Paper",
    "item.minecraft.wither_skeleton_skull": "Wither Skeleton Skull",
    "item.minecraft.oxeye_daisy": "Oxeye Daisy",
}


def shaped(pattern, key, result, count=1):
    return {"type": "minecraft:crafting_shaped", "pattern": pattern, "key": key,
            "result": {"id": f"minecraft:{result}", "count": count}}


def shapeless(ingredients, result, count=1):
    return {"type": "minecraft:crafting_shapeless", "ingredients": ingredients,
            "result": {"id": f"minecraft:{result}", "count": count}}


BASE_RECIPES = {
    "stick": shaped(["#", "#"], {"#": "#minecraft:planks"}, "stick", 4),
    "oak_planks": shapeless(["minecraft:oak_log"], "oak_planks", 4),
    "torch": shaped(["X", "#"], {"X": ["minecraft:coal", "minecraft:charcoal"], "#": "minecraft:stick"}, "torch", 4),
    "iron_block": shaped(["###", "###", "###"], {"#": "minecraft:iron_ingot"}, "iron_block"),
    "iron_ingot_from_iron_block": shapeless(["minecraft:iron_block"], "iron_ingot", 9),
    "iron_ingot_from_nuggets": shaped(["###", "###", "###"], {"#": "minecraft:iron_nugget"}, "iron_ingot"),
    "iron_nugget": shapeless(["minecraft:iron_ingot"], "iron_nugget", 9),
    "iron_ingot_from_smelting_raw_iron": {"type": "minecraft:smelting", "ingredient": "minecraft:raw_iron",
                                          "result": {"id": "minecraft:iron_ingot"}},
    "red_bundle": {"type": "minecraft:crafting_transmute", "input": "#minecraft:bundles",
                   "material": "minecraft:red_dye", "result": {"id": "minecraft:red_bundle"}},
    "skull_banner_pattern": shapeless(["minecraft:paper", "minecraft:wither_skeleton_skull"], "skull_banner_pattern"),
    "flower_banner_pattern": shapeless(["minecraft:paper", "minecraft:oxeye_daisy"], "flower_banner_pattern"),
    "repair_item": {"type": "minecraft:crafting_special_repairitem"},
}

TAGS = {
    "planks": {"values": ["minecraft:oak_planks", "#minecraft:more_planks"]},
    "more_planks": {"values": ["minecraft:spruce_planks", "minecraft:oak_planks"]},
    "bundles": {"values": ["minecraft:bundle", "minecraft:red_bundle"]},
}


def jar(recipes=None, tags=None):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as z:
        for name, data in (recipes or BASE_RECIPES).items():
            z.writestr(f"data/minecraft/recipe/{name}.json", json.dumps(data))
        for name, data in (tags or TAGS).items():
            z.writestr(f"data/minecraft/tags/item/{name}.json", json.dumps(data))
        z.writestr("assets/minecraft/lang/en_us.json", json.dumps(LANG))
    return zipfile.ZipFile(buffer)


class BuildBookTest(unittest.TestCase):
    def setUp(self):
        self.book = gen.build_book(jar())

    def test_a_shaped_recipe_counts_each_symbol_and_knows_it_fits_two_by_two(self):
        torch = self.book["recipes"]["torch"]
        self.assertEqual(torch["count"], 4)
        self.assertTrue(torch["small"])
        self.assertEqual(torch["ingredients"], [{"count": 1, "items": ["coal", "charcoal"]}, {"count": 1, "items": ["stick"]}])
        self.assertFalse(self.book["recipes"]["iron_block"]["small"])

    def test_tags_are_expanded_in_order_without_repeats(self):
        self.assertEqual(self.book["tags"]["planks"], ["oak_planks", "spruce_planks"])
        self.assertEqual(self.book["recipes"]["stick"]["ingredients"], [{"count": 2, "tag": "planks"}])

    def test_only_the_unpacking_direction_is_flagged(self):
        recipes = self.book["recipes"]
        self.assertTrue(recipes["iron_ingot_from_iron_block"]["unpack"])
        self.assertTrue(recipes["iron_nugget"]["unpack"])
        self.assertFalse(recipes["iron_block"]["unpack"])
        self.assertFalse(recipes["iron_ingot_from_nuggets"]["unpack"])

    def test_furnace_outputs_are_raw_and_recolouring_does_not_make_an_item_raw(self):
        items = self.book["items"]
        self.assertTrue(items["iron_ingot"]["raw"])
        self.assertFalse(items["red_bundle"]["raw"])
        self.assertFalse(items["stick"]["raw"])

    def test_skipped_and_cooking_recipes_are_left_out(self):
        self.assertNotIn("repair_item", self.book["recipes"])
        self.assertNotIn("iron_ingot_from_smelting_raw_iron", self.book["recipes"])

    def test_clashing_names_take_the_description(self):
        self.assertEqual(self.book["items"]["skull_banner_pattern"]["name"], "Skull Charge Banner Pattern")
        self.assertEqual(self.book["items"]["flower_banner_pattern"]["name"], "Flower Charge Banner Pattern")

    def test_an_unknown_recipe_type_stops_the_run(self):
        recipes = {**BASE_RECIPES, "odd": {"type": "minecraft:crafting_new_thing", "result": {"id": "minecraft:stick"}}}
        with self.assertRaises(SystemExit):
            gen.build_book(jar(recipes))
        self.assertIn("stick", gen.build_book(jar(recipes), allow_unknown=True)["recipes"])

    def test_the_old_ingredient_format_is_read(self):
        recipes = {"stick": shaped(["#", "#"], {"#": {"tag": "minecraft:planks"}}, "stick", 4),
                   "oak_planks": shapeless([{"item": "minecraft:oak_log"}], "oak_planks", 4)}
        book = gen.build_book(jar(recipes))
        self.assertEqual(book["recipes"]["oak_planks"]["ingredients"], [{"count": 1, "items": ["oak_log"]}])


if __name__ == "__main__":
    unittest.main()
