# Extending Minelark from a mod

Minelark keeps a curated, name-based API for pack scripts: options like `block(sound=...)`,
`item(tool_tier=...)`, and `block(shape=...)` take a known name, and a typo fails fast. That set of
names is not closed, though - a companion Fabric mod can register more of them so packs can use the
mod's content by name.

This is a small Java API **for mods**, not something scripts can reach. The script sandbox is
unchanged: packs only gain new valid names, never access to Java.

## What can be extended

| Option | Extensible? | How |
|---|---|---|
| `block(sound=...)` | Yes | `MinelarkTypes.sound(name, group)` |
| `item(tool_tier=...)` | Yes | `MinelarkTypes.toolTier(name, material)` |
| `block(shape=...)` | Yes | `MinelarkTypes.shape(name, factory, assets)` |
| `item(armor_material=...)` | Automatic | armor materials are a game registry, so `namespace:id` works with no addon |
| `item(rarity=...)` | No | rarity is a fixed vanilla enum; nothing can extend it |

Armor materials are the special case: because the game stores them in a registry, a script can already
write `armor_material="somemod:copper"` and Minelark resolves it straight from the registry. Registering
an alias with `MinelarkTypes.armorMaterial(...)` just lets packs use a short name instead of the full id.

Names are yours to choose. Prefix them with your mod id (`"example:cogs"`) to avoid clashing with
another addon; a bare name works too (the built-ins are bare, like `"stone"`).

## Registering types

Declare a `MinelarkTypesInitializer` under the `minelark:types` entrypoint in your `fabric.mod.json`:

```json
"entrypoints": {
  "minelark:types": ["com.example.ExampleMinelarkTypes"]
}
```

Then register your types. This example is complete - the sound group is built from vanilla sound
events, so you can copy it as-is and swap in your own:

```java
package com.example;

import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import ru.nelande.minelark.api.MinelarkTypes;
import ru.nelande.minelark.api.MinelarkTypesInitializer;

public final class ExampleMinelarkTypes implements MinelarkTypesInitializer {
    @Override
    public void registerMinelarkTypes() {
        // A sound group (here reusing vanilla metal sounds; use your own SoundEvents for custom audio).
        BlockSoundGroup cogs = new BlockSoundGroup(
                1.0f, 1.0f,
                SoundEvents.BLOCK_METAL_BREAK, SoundEvents.BLOCK_METAL_STEP,
                SoundEvents.BLOCK_METAL_PLACE, SoundEvents.BLOCK_METAL_HIT, SoundEvents.BLOCK_METAL_FALL);
        MinelarkTypes.sound("example:cogs", cogs);

        // A tool tier: pass your mod's net.minecraft.item.ToolMaterial.
        MinelarkTypes.toolTier("example:brass", ExampleMaterials.BRASS);

        // An armor-material alias (optional - a full "example:copper" id already works without this).
        MinelarkTypes.armorMaterial("example:copper", ExampleMaterials.COPPER_ARMOR);
    }
}
```

`ToolMaterial` is the vanilla `net.minecraft.item.ToolMaterial` interface (durability, mining speed,
attack damage, ...), and `armorMaterial(...)` takes a `RegistryEntry<ArmorMaterial>` - exactly what you
already have from registering your mod's materials.

Minelark invokes every `minelark:types` initializer during its own init, **before** startup scripts
run, so the names are valid by the time a pack uses them. A pack can then write:

```python
block("cog_casing", sound = "example:cogs")
item("brass_pickaxe", tool_type = "pickaxe", tool_tier = "example:brass")
item("copper_helmet", armor_slot = "head", armor_material = "example:copper")
```

If a script uses a name no addon registered, Minelark rejects it up front with the list of valid names
- the same fail-fast behaviour as the built-ins.

## Custom shapes

A shape needs both a block factory (`Shape`, a `Settings -> Block` function) and its resource-pack
assets (`ShapeAssets`: blockstate, block models, the item's parent model, and any extra loot
functions). Both are plain - the assets are pure JSON strings, so no Minecraft types leak in:

```java
MinelarkTypes.shape("example:post",
    settings -> new PostBlock(settings),           // Shape: your block, from Minelark's Settings
    new ShapeAssets() {
        public String blockstate(String id) { return /* blockstate JSON */; }
        public Map<String, String> blockModels(String id) { return /* model name -> JSON */; }
        public String itemParentModel(String id) { return id + "_inventory"; }
        // selfDropLootFunctions(id) defaults to "" - override only for things like a slab's double drop.
    });
```

Textures still come from the pack author's `minelark/assets/` folder, exactly like the built-in
slab / stairs / fence / wall shapes.

## How it fits together

1. Minelark registers its built-in types (`stone`, `wood`, `iron`, ...).
2. It calls every mod's `minelark:types` initializer - your names join the set.
3. It builds a catalog of the valid names and hands it to the script layer.
4. Startup scripts run; `block(sound=...)` / `item(tool_tier=...)` / `block(shape=...)` validate
   against that catalog and fail fast on an unknown name.
