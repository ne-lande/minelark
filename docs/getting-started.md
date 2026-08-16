# Getting Started

## Where scripts go

Minelark loads scripts from a `minelark/` folder in your instance directory (next to `mods/`,
`saves/`, `options.txt`). The folder and an example script are created for you the first time you
launch the game with Minelark installed:

```
<instance>/minelark/
├── startup/   # runs once at launch, before registries freeze: items, blocks
├── server/    # runs on world load and /minelark reload: recipes, events
└── client/    # runs once on client start: tooltips, client chat, HUD, the client tick
```

Any file ending in `.star` inside a phase folder gets run, in alphabetical order. Scripts are
independent: if one throws, Minelark logs it and keeps going with the rest.

### The three phases

| Folder | When it runs | What it's for |
|---|---|---|
| `startup/` | Once at launch, before the item/block registries freeze | Registering content |
| `server/`  | On world load, and again on `/minelark reload` | Recipes, events, commands |
| `client/`  | Once when the client starts | [Client-side hooks](api/client.md): tooltips, chat, HUD, tick |

Every phase gives you `print`, the [`log`](api/common.md) namespace, and
[`load()`](#sharing-code-with-load). The content builtins differ by phase, and the list is still
growing (see the [roadmap](roadmap.md)).

## Your first item

Make a file at `<instance>/minelark/startup/my_pack.star`:

```python
print("Loading my pack!")

item("ruby")
item("sapphire", max_stack_size = 16)
```

Launch the game and check the log:

```
[minelark] [my_pack.star] Loading my pack!
[minelark] Registered item minelark:ruby
[minelark] Registered item minelark:sapphire
```

You now have `minelark:ruby` and `minelark:sapphire`, and they show up in the Ingredients creative
tab.

One thing to know up front: Minelark doesn't generate models or textures yet, so new items and
blocks render as the missing-texture placeholder. They work fine otherwise; textures are on the
[roadmap](roadmap.md).

### More options, and blocks

`item()` has options for durability, rarity, fireproofing, food, and fuel. `block()` registers a
block plus its item, with the usual settings:

```python
item("ruby_sword", max_damage = 250, rarity = "rare", display_name = "Ruby Sword")
item("trail_mix", nutrition = 6, saturation = 0.8)   # edible
item("coal_chunk", burn_time = 1600)                 # burns in a furnace

block("marble", hardness = 1.5, resistance = 6.0, display_name = "Marble")
block("glow_crystal", luminance = 15, requires_tool = True)
block("ruby_ore", drops = "minelark:ruby")           # drops a ruby, not itself
```

By default a block drops itself. Set `drops` to another item id, or to `"none"` if it should drop
nothing. `display_name` sets the shown name directly, so you don't need a language file. The
[startup reference](api/startup.md) lists every option.

### Handles

`item()` and `block()` hand back a reference to what you just made, so you don't have to retype the
id. A handle works anywhere an id string does:

```python
ruby = item("ruby", rarity = "rare")
block("ruby_ore", drops = ruby)          # same as drops = "minelark:ruby"
```

Handles only exist inside the script that created them. Items live in `startup/` and recipes in
`server/`, so a recipe still refers to your items by string id.

### Tags

`tags` adds an item or block to one or more tags. A bare name (no namespace) gets the conventional
`c:` prefix, so `"gems"` means `c:gems`:

```python
item("ruby", tags = ["c:gems"])
block("marble", tags = ["minecraft:mineable/pickaxe"])
```

Minelark applies tags through a small generated datapack that loads automatically. To check what an
item ended up in, run `/minelark tags <id>` in-game (operator only):

```
/minelark tags minelark:ruby
```

## Reloading

`server/` scripts reload without restarting: run `/minelark reload` (needs operator permission). It
re-runs every server script and tells you how many ran. `startup/` scripts register content before
the registries freeze, so those changes need a game restart. `client/` scripts run once at client
start.

## Recipes

Recipes go in `server/` scripts, so they reload with `/minelark reload`:

```python
# server/my_recipes.star
recipes.shaped("minelark:ruby_block", ["RRR", "RRR", "RRR"], {"R": "minelark:ruby"})
recipes.shapeless("minelark:ruby", ["minelark:sapphire"])
recipes.smelting("minelark:ruby", "minelark:ruby_ore", experience = 1.0)
```

An id or tag without a namespace defaults to `minecraft:`, and a `#` prefix means a tag
(`"#c:ingots"`). The [server reference](api/server.md) covers the rest of the recipe types.

## Reacting to events

A `server/` script can run code when something happens in game. Events are grouped by namespace, and
your callback takes the event context as `ctx`:

```python
# server/events.star
def on_join(ctx):
    ctx.player.tell(text("Welcome, ").append(text(ctx.player.name).color("gold")))

def no_bedrock(ctx):
    if ctx.block == "minecraft:bedrock":
        ctx.cancel()

events.minelark.PLAYER_JOINED.on(on_join)
events.minelark.BLOCK_BROKEN.on(no_bedrock)
```

There's a spread of events - player join/leave/death/chat, block break/place, server tick, commands,
explosions. Their `ctx` carries the relevant data (`ctx.player`, `ctx.block`, `ctx.x`, ...), some can
be cancelled with `ctx.cancel()`, and a few fields can be rewritten in place (like a chat
`ctx.message`). Event names are typed, so a typo fails right away instead of quietly doing nothing.
The [events reference](api/events.md) lists them all, along with the player / level / item wrappers.

## Styled text

Messages can be plain strings or styled components built with `text(...)`:

```python
ctx.player.tell(text("Careful!").color("red").bold())
```

Chain `.color`, `.bold`, `.hover`, `.click_run`, `.append`, and more. See the
[text reference](api/text.md).

## Your own commands

A `server/` script can register `/commands` that run your code:

```python
def cmd_heal(ctx):
    ctx.source.tell(text("Healed!").color("green"))

commands.register("heal", cmd_heal, permission = 2)
```

Arguments, permission levels, and the command `ctx` are in the [commands reference](api/commands.md).

## Sharing code with `load()`

Pull shared helpers into their own file and import them with `load()`. Paths are relative to the
phase folder. Keep helpers in a subfolder so they don't run as top-level scripts on their own:

```
minelark/startup/
├── pack.star
└── lib/
    └── gems.star
```

```python
# lib/gems.star
DEFAULT_STACK = 16

def gem(name):
    item(name, max_stack_size = DEFAULT_STACK)
```

```python
# pack.star
load("lib/gems.star", "gem")

gem("ruby")
gem("sapphire")
```

Each imported file runs once and is cached. If two files import each other, Minelark reports the
cycle instead of hanging.

## Next steps

- [The Starlark Language](language.md) if you're new to Starlark.
- [Startup API Reference](api/startup.md) for the full list of item and block options.
