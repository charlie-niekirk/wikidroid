#!/usr/bin/env python3
"""Generates the resource calculator's crafting recipes from Minecraft: Java Edition's own data.

Downloads the client jar for one release from Mojang's version manifest, reads its crafting recipes, item tags
and English names, and writes core/data/src/main/assets/recipes/java.json. Only facts are kept (ingredients,
counts, names); no game file is copied into the repository. See README.md in this directory.

Standard library only, so it runs anywhere python3 does.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import urllib.parse
import urllib.request
import zipfile

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = REPO_ROOT / "core/data/src/main/assets/recipes/java.json"
DEFAULT_CACHE = REPO_ROOT / "build/recipe-cache"
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
WIKI_API = "https://minecraft.wiki/api.php"
USER_AGENT = "WikiDroid-recipe-generator/1 (https://github.com/charlie-niekirk/wikidroid)"
FORMAT = 1

# Crafting recipe types the calculator follows. Each has a reader in `read_recipe`.
CRAFTING_TYPES = {
    "minecraft:crafting_shaped",
    "minecraft:crafting_shapeless",
    "minecraft:crafting_transmute",
    "minecraft:crafting_imbue",
    "minecraft:crafting_decorated_pot",
}

# Types the calculator does not follow. Cooking types still matter: their outputs are raw by default.
COOKING_TYPES = {
    "minecraft:smelting",
    "minecraft:blasting",
    "minecraft:smoking",
    "minecraft:campfire_cooking",
}
SKIPPED_TYPES = COOKING_TYPES | {
    "minecraft:stonecutting",
    "minecraft:brewing",
    "minecraft:smithing_trim",
    "minecraft:smithing_transform",
    # Dyeing leather armour gives back the same item.
    "minecraft:crafting_dye",
}
SKIPPED_PREFIXES = ("minecraft:crafting_special_",)

# Items players gather rather than craft, although a crafting recipe makes them.
RAW_OVERRIDES = {
    "leather",  # four rabbit hides make one, but cows drop it
    "honey_bottle",  # its recipe takes a honey block, which is made of honey bottles
}

# Display names that would otherwise clash or differ from the wiki's page names.
NAME_OVERRIDES = {
    "disc_fragment_5": "Disc Fragment 5",
    "music_disc_5": "Music Disc 5",
}


def main(argv=None):
    args = parse_args(argv)
    previous = read_previous(args.output)
    version = choose_version(args, previous)
    jar_path, sha1 = download_client(version, args.cache_dir)
    with zipfile.ZipFile(jar_path) as jar:
        book = build_book(jar, allow_unknown=args.allow_unknown)
    icons = check_icons(book["items"]) if args.check_icons else previous_icons(previous)
    write_book(args.output, version, sha1, book, icons)
    print(
        f"Wrote {args.output.relative_to(REPO_ROOT)}: Minecraft {version}, {len(book['items'])} items, "
        f"{len(book['recipes'])} recipes, {len(book['tags'])} tags, {sum(1 for i in book['items'].values() if i['raw'])} raw"
    )


def parse_args(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    which = parser.add_mutually_exclusive_group()
    which.add_argument("--latest", action="store_true", help="use the manifest's latest release")
    which.add_argument("--version", help="a release id such as 26.3")
    parser.add_argument("--check-icons", action="store_true", help="ask minecraft.wiki which Invicon files exist")
    parser.add_argument("--allow-unknown", action="store_true", help="skip recipe types this script does not know")
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--cache-dir", type=pathlib.Path, default=DEFAULT_CACHE)
    return parser.parse_args(argv)


def read_previous(path):
    if not path.exists():
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def choose_version(args, previous):
    if args.version:
        return args.version
    if args.latest or previous is None:
        return fetch_json(MANIFEST_URL)["latest"]["release"]
    return previous["minecraftVersion"]


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def fetch_json(url):
    return json.loads(fetch(url))


def download_client(version, cache_dir):
    """The client jar for [version], cached under [cache_dir] and checked against the manifest's sha1."""
    manifest = fetch_json(MANIFEST_URL)
    entry = next((v for v in manifest["versions"] if v["id"] == version), None)
    if entry is None:
        sys.exit(f"Minecraft {version} is not in the version manifest")
    client = fetch_json(entry["url"])["downloads"]["client"]
    jar_path = cache_dir / version / "client.jar"
    if not jar_path.exists() or sha1_of(jar_path) != client["sha1"]:
        jar_path.parent.mkdir(parents=True, exist_ok=True)
        print(f"Downloading the {version} client jar ({client['size'] // 1_000_000} MB)")
        jar_path.write_bytes(fetch(client["url"]))
    if sha1_of(jar_path) != client["sha1"]:
        sys.exit(f"{jar_path} does not match the manifest's sha1")
    return jar_path, client["sha1"]


def sha1_of(path):
    return hashlib.sha1(path.read_bytes()).hexdigest()


def strip(resource_id):
    """`minecraft:stick` -> `stick`, `#minecraft:planks` -> `#planks`."""
    tag = resource_id.startswith("#")
    name = resource_id.lstrip("#").removeprefix("minecraft:")
    return f"#{name}" if tag else name


def json_files(jar, *folders):
    """`{relative id: parsed json}` for every json file under the first of [folders] the jar has."""
    for folder in folders:
        prefix = f"data/minecraft/{folder}/"
        files = {
            name[len(prefix) : -len(".json")]: json.loads(jar.read(name))
            for name in jar.namelist()
            if name.startswith(prefix) and name.endswith(".json")
        }
        if files:
            return files
    return {}


def build_book(jar, allow_unknown=False):
    raw_recipes = json_files(jar, "recipe", "recipes")
    tag_files = json_files(jar, "tags/item", "tags/items")
    lang = json.loads(jar.read("assets/minecraft/lang/en_us.json"))

    recipes, cooked, unknown = {}, set(), set()
    for recipe_id, data in sorted(raw_recipes.items()):
        kind = data.get("type", "")
        if kind in COOKING_TYPES:
            cooked.add(strip(data["result"]["id"]))
        if kind in CRAFTING_TYPES:
            recipe = read_recipe(recipe_id, data)
            if recipe is not None:
                recipes[recipe_id] = recipe
        elif kind not in SKIPPED_TYPES and not kind.startswith(SKIPPED_PREFIXES):
            unknown.add(kind)
    if unknown and not allow_unknown:
        sys.exit(f"Unknown recipe types (decide how to treat them, or pass --allow-unknown): {sorted(unknown)}")

    tags = resolve_tags(tag_files, used_tags(recipes))
    mark_unpacking(recipes)
    item_ids = recipe_item_ids(recipes, tags)
    names = item_names(item_ids, lang)
    raw = raw_by_default(item_ids, recipes, tags, cooked)
    items = {item: {"name": names[item], "raw": item in raw} for item in sorted(item_ids)}
    check_book(items, recipes, tags)
    return {"items": items, "recipes": recipes, "tags": tags}


def read_recipe(recipe_id, data):
    kind = data["type"]
    result = data.get("result", {})
    if "id" not in result:
        return None  # map cloning and other recipes whose result depends on the input
    if kind == "minecraft:crafting_shaped":
        ingredients, small = shaped_ingredients(data)
    elif kind == "minecraft:crafting_shapeless":
        ingredients = merge([(1, read_ingredient(i)) for i in data["ingredients"]])
        small = len(data["ingredients"]) <= 4
    elif kind == "minecraft:crafting_transmute":
        # Recolours an item: the input's own colour is removed by the planner, never needed for itself.
        ingredients = merge([(1, read_ingredient(data["input"])), (1, read_ingredient(data["material"]))])
        small = True
    elif kind == "minecraft:crafting_imbue":
        # Eight of the material around one source, as with tipped arrows.
        ingredients = merge([(8, read_ingredient(data["material"])), (1, read_ingredient(data["source"]))])
        small = False
    else:  # crafting_decorated_pot: four sides, each a brick or a sherd
        sides = [data[side] for side in ("back", "front", "left", "right")]
        ingredients = merge([(1, read_ingredient(side)) for side in sides])
        small = False
    return {
        "result": strip(result["id"]),
        "count": result.get("count", 1),
        "small": small,
        "ingredients": ingredients,
    }


def shaped_ingredients(data):
    pattern = data["pattern"]
    counts = {}
    for row in pattern:
        for symbol in row:
            if symbol != " ":
                counts[symbol] = counts.get(symbol, 0) + 1
    ingredients = merge([(count, read_ingredient(data["key"][symbol])) for symbol, count in counts.items()])
    small = len(pattern) <= 2 and max(len(row) for row in pattern) <= 2
    return ingredients, small


def read_ingredient(value):
    """An ingredient as `("tag", name)` or `("items", (id, ...))`."""
    if isinstance(value, dict):  # the format before 1.21.2
        value = f"#{value['tag']}" if "tag" in value else value["item"]
    if isinstance(value, str) and value.startswith("#"):
        return ("tag", strip(value)[1:])
    values = value if isinstance(value, list) else [value]
    options = []
    for option in values:
        if isinstance(option, dict):
            option = option["item"]
        options.append(strip(option))
    return ("items", tuple(options))


def merge(pairs):
    """Adds up the counts of identical ingredients, keeping first-seen order."""
    totals = {}
    for count, ingredient in pairs:
        totals[ingredient] = totals.get(ingredient, 0) + count
    return [{"count": count, ingredient[0]: list(ingredient[1]) if ingredient[0] == "items" else ingredient[1]}
            for ingredient, count in totals.items()]


def used_tags(recipes):
    return {i["tag"] for recipe in recipes.values() for i in recipe["ingredients"] if "tag" in i}


def resolve_tags(tag_files, wanted):
    """`{tag: [item, ...]}` for [wanted], nested tags expanded in place, vanilla order kept."""

    def expand(tag, seen):
        if tag in seen:
            return []
        seen = seen | {tag}
        items = []
        for value in tag_files.get(tag, {}).get("values", []):
            value = value["id"] if isinstance(value, dict) else value
            value = strip(value)
            for item in expand(value[1:], seen) if value.startswith("#") else [value]:
                if item not in items:
                    items.append(item)
        return items

    tags = {tag: expand(tag, frozenset()) for tag in sorted(wanted)}
    empty = [tag for tag, items in tags.items() if not items]
    if empty:
        sys.exit(f"Tags with no items: {empty}")
    return tags


def single_item(recipe):
    """The one item a recipe takes, if it takes exactly one kind of item (not a tag)."""
    ingredients = recipe["ingredients"]
    if len(ingredients) == 1 and len(ingredients[0].get("items", [])) == 1:
        return ingredients[0]["items"][0]
    return None


def mark_unpacking(recipes):
    """Flags storage unpacking (one iron block -> nine ingots): one item in, several out, and a recipe packs them back.

    The planner never picks these by itself, or every ingot would be broken down into a fraction of a block.
    """
    packs = {(single_item(r), r["result"]) for r in recipes.values() if single_item(r)}
    for recipe in recipes.values():
        source = single_item(recipe)
        recipe["unpack"] = (
            source is not None
            and recipe["ingredients"][0]["count"] == 1
            and recipe["count"] > 1
            and (recipe["result"], source) in packs
        )


def options(ingredient, tags):
    return tags[ingredient["tag"]] if "tag" in ingredient else ingredient["items"]


def recipe_item_ids(recipes, tags):
    ids = set()
    for recipe in recipes.values():
        ids.add(recipe["result"])
        for ingredient in recipe["ingredients"]:
            ids.update(options(ingredient, tags))
    return ids


def item_names(item_ids, lang):
    """English names, made unique: a clash takes the item's description ("Skull Charge Banner Pattern") or its id."""
    names = {}
    for item in item_ids:
        name = lang.get(f"item.minecraft.{item}") or lang.get(f"block.minecraft.{item}")
        if name is None:
            sys.exit(f"No English name for {item}")
        names[item] = name
    clashing = {name for name in names.values() if list(names.values()).count(name) > 1}
    for item, name in names.items():
        if name in clashing:
            description = lang.get(f"item.minecraft.{item}.desc")
            names[item] = f"{description} {name}" if description else item.replace("_", " ").title()
    names.update({item: name for item, name in NAME_OVERRIDES.items() if item in names})
    duplicates = sorted({name for name in names.values() if list(names.values()).count(name) > 1})
    if duplicates:
        sys.exit(f"Item names are not unique, add NAME_OVERRIDES for: {duplicates}")
    return names


def raw_by_default(item_ids, recipes, tags, cooked):
    """Items the planner treats as raw unless the user asks to craft them.

    Anything a furnace makes (ingots, glass, gems from ores), items whose only real recipe needs the item itself
    (smithing template duplication), and [RAW_OVERRIDES]. Items with no usable recipe are raw anyway.
    """
    by_result = {}
    for recipe in recipes.values():
        if not recipe["unpack"]:
            by_result.setdefault(recipe["result"], []).append(recipe)
    # An ingredient that can only be the item itself; a recolouring that accepts any colour does not count.
    self_consuming = {
        item
        for item, made_by in by_result.items()
        if all(any(options(i, tags) == [item] for i in recipe["ingredients"]) for recipe in made_by)
    }
    return (cooked | self_consuming | RAW_OVERRIDES) & item_ids


def check_book(items, recipes, tags):
    for recipe_id, recipe in recipes.items():
        ids = [recipe["result"]] + [o for i in recipe["ingredients"] for o in options(i, tags)]
        dangling = [i for i in ids if i not in items]
        if dangling:
            sys.exit(f"{recipe_id} names unknown items {dangling}")
        if not recipe["ingredients"]:
            sys.exit(f"{recipe_id} has no ingredients")


def icon_name(name):
    return "Invicon_" + name.replace(" ", "_") + ".png"


def previous_icons(previous):
    if previous is None:
        return {}
    return {item["id"]: item.get("icon") for item in previous["items"]}


def check_icons(items):
    """`{item: file name or None}`: the item's Invicon on minecraft.wiki, a GIF when there is no PNG, else None."""
    wanted = {item: icon_name(data["name"]) for item, data in items.items()}
    found = existing_files(set(wanted.values()))
    missing = {item: name[: -len(".png")] + ".gif" for item, name in wanted.items() if name not in found}
    found_gifs = existing_files(set(missing.values()))
    icons = {}
    for item, name in wanted.items():
        if name in found:
            icons[item] = name
        elif missing[item] in found_gifs:
            icons[item] = missing[item]
        else:
            icons[item] = None
    absent = sorted(item for item, icon in icons.items() if icon is None)
    print(f"{len(absent)} items have no Invicon on the wiki: {absent}")
    return icons


def existing_files(file_names):
    """The subset of [file_names] that exist as files on minecraft.wiki (redirects followed)."""
    found = set()
    names = sorted(file_names)
    for start in range(0, len(names), 50):
        batch = names[start : start + 50]
        query = urllib.parse.urlencode(
            {
                "action": "query",
                "format": "json",
                "formatversion": "2",
                "redirects": "1",
                "titles": "|".join(f"File:{name}" for name in batch),
            }
        )
        response = fetch_json(f"{WIKI_API}?{query}")
        redirects = {r["from"]: r["to"] for r in response["query"].get("redirects", [])}
        normalized = {n["from"]: n["to"] for n in response["query"].get("normalized", [])}
        present = {page["title"] for page in response["query"]["pages"] if not page.get("missing")}
        for name in batch:
            title = normalized.get(f"File:{name}", f"File:{name}")
            if redirects.get(title, title) in present:
                found.add(name)
    return found


def write_book(path, version, sha1, book, icons):
    """One entry per line, sorted, so a regeneration shows as a readable diff."""
    compact = {"separators": (",", ":"), "ensure_ascii": False}
    items = []
    for item, data in book["items"].items():
        entry = {"id": item, "name": data["name"], "icon": icons.get(item, icon_name(data["name"]))}
        if data["raw"]:
            entry["raw"] = True
        items.append(entry)
    recipes = []
    for recipe_id, recipe in sorted(book["recipes"].items()):
        entry = {"id": recipe_id, **recipe}
        if not entry["unpack"]:
            del entry["unpack"]
        recipes.append(entry)
    tags = [{"id": tag, "items": items_} for tag, items_ in sorted(book["tags"].items())]

    def lines(entries):
        return ",\n".join(json.dumps(entry, **compact) for entry in entries)

    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(f'{{"format":{FORMAT},"minecraftVersion":{json.dumps(version)},"clientSha1":{json.dumps(sha1)},\n')
        f.write(f'"items":[\n{lines(items)}],\n')
        f.write(f'"tags":[\n{lines(tags)}],\n')
        f.write(f'"recipes":[\n{lines(recipes)}]}}\n')


if __name__ == "__main__":
    main()
