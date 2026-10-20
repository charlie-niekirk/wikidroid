# Crafting recipes

`generate_recipes.py` writes `core/data/src/main/assets/recipes/java.json`, the recipe book behind the resource
calculator (Tools tab). It needs only `python3`.

```
python3 scripts/recipes/generate_recipes.py                 # regenerate for the version already in the asset
python3 scripts/recipes/generate_recipes.py --latest         # move to Mojang's latest release
python3 scripts/recipes/generate_recipes.py --version 26.3   # a specific release
python3 scripts/recipes/generate_recipes.py --check-icons    # also ask minecraft.wiki which Invicon files exist
python3 -m unittest discover -s scripts/recipes              # the script's own tests
```

It downloads that release's client jar from Mojang's version manifest into `build/recipe-cache/` (never committed),
checks its sha1, and reads `data/minecraft/recipe`, `data/minecraft/tags/item` and the English lang file. Only facts
are written: ingredients, counts and names. Never edit the JSON by hand; change the script and rerun it, and update
`core/data/NOTICE.md` with the new version and sha1.

What it keeps:

- Crafting only: shaped, shapeless, transmute (recolouring), imbue (tipped arrows) and the decorated pot. Special
  recipes (banner copying, firework stars, repairs) and every non-crafting type are skipped. An unknown type stops the
  run, so a new version's recipe type gets a decision instead of being dropped silently (`--allow-unknown` overrides).
- `unpack` on storage unpacking (one block to nine ingots) when a recipe packs the result back. The calculator never
  picks those by itself.
- `raw` on items it treats as raw unless the user asks to craft them: anything a furnace, smoker or campfire makes
  (ingots, glass, gems), items whose only recipe needs the item itself (smithing template duplication), and
  `RAW_OVERRIDES` (gathered items such as leather).
- `icon`: the item's `Invicon_<Name>.png` on minecraft.wiki. Without `--check-icons` the previous file names are
  reused; with it, missing PNGs fall back to a GIF or to no icon. `NAME_OVERRIDES` fixes names that clash or differ
  from the wiki.
