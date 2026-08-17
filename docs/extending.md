# Extending Minelark from a mod

Minelark keeps a curated, name-based API for pack scripts: options like `block(sound=...)`,
`item(tool_tier=...)`, and `block(shape=...)` take a known name, and a typo fails fast. That set of
names is not closed, though. A companion Fabric mod can register more of them so packs can use a
mod's content by name.

This is a Java API for mods, not something scripts can reach. The script sandbox is unchanged: packs
only gain new valid names, never access to Java.

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

## Registering types

Implement `MinelarkTypesInitializer` and declare it under the `minelark:types` entrypoint in your
`fabric.mod.json`:

```json
"entrypoints": {
  "minelark:types": ["com.example.ExampleMinelarkTypes"]
}
```

```java
public final class ExampleMinelarkTypes implements MinelarkTypesInitializer {
    @Override
    public void registerMinelarkTypes() {
        MinelarkTypes.sound("example:cogs", ExampleSounds.COGS);
        MinelarkTypes.toolTier("example:brass", ExampleToolMaterials.BRASS);
        MinelarkTypes.armorMaterial("example:copper", ExampleArmors.COPPER);
    }
}
```

Minelark invokes every `minelark:types` initializer during its own init, before startup scripts run,
so the names are valid by the time a pack uses them. A pack can then write:

```python
block("cog_casing", sound = "example:cogs")
item("brass_pickaxe", tool_type = "pickaxe", tool_tier = "example:brass")
```

## Custom shapes

A shape needs both a block factory and its resource-pack assets (blockstate, models, item model, and
any loot functions). Provide a `ShapeAssets` alongside the block factory:

```java
MinelarkTypes.shape("example:post",
    settings -> new PostBlock(settings),
    new ShapeAssets() {
        public String blockstate(String id) { return /* blockstate JSON */; }
        public Map<String, String> blockModels(String id) { return /* model name -> JSON */; }
        public String itemParentModel(String id) { return id + "_inventory"; }
    });
```

Textures still come from the pack author's `minelark/assets/` folder, the same as the built-in shapes.
