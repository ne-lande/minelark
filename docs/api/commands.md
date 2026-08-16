# Commands API Reference

Server scripts can register their own `/commands` with the `commands` namespace. Each command runs a
Starlark handler that receives a command context, `ctx`.

```python
def greet(ctx):
    ctx.source.tell("Hello, " + ctx.args["who"] + "!")

commands.register("greet", greet, args = [{"name": "who", "type": "word"}])
```

Now `/greet Steve` replies "Hello, Steve!".

## Registering

`commands.register(name, handler, permission = 0, args = [])`

| Argument | Meaning |
|---|---|
| `name` | The command as typed. Space-separated words become sub-commands, e.g. `"warp home"` is `/warp home`. Use lowercase letters, digits, and underscores. |
| `handler` | A `def handler(ctx): ...` to run when the command is used. |
| `permission` | The op level required: `0` (everyone, the default) up to `4`. |
| `args` | The command's arguments (see below). |

Commands are registered when the server starts and are refreshed by `/minelark reload`, so editing a
script and reloading updates your commands in place.

## Arguments

`args` is a list of argument specs, each `{"name": ..., "type": ...}` (a `[name, type]` pair also
works). They must be given in order, and all are required.

| Type | Parsed as | Value in `ctx.args` |
|---|---|---|
| `word` | A single word | string |
| `string` | The rest of the line | string |
| `int` | A whole number | int |
| `float` | A decimal number | float |
| `bool` | `true` / `false` | bool |
| `player` | An online player | a [player](events.md#wrappers) |

```python
def pay(ctx):
    target = ctx.args["target"]   # a player
    amount = ctx.args["amount"]   # an int
    target.tell("You received " + str(amount) + " coins from " + ctx.source.name)

commands.register("pay", pay, permission = 0, args = [
    {"name": "target", "type": "player"},
    {"name": "amount", "type": "int"},
])
```

## The command `ctx`

| Field | Value |
|---|---|
| `ctx.args` | A dict of the parsed arguments, keyed by name. |
| `ctx.source` | Who ran the command (below). |

`ctx.source`:

| Member | Value |
|---|---|
| `.name` | The name of whoever ran it. |
| `.is_player` | Whether a player (not the console) ran it. |
| `.player` | The [player](events.md#wrappers), or `None` if the console ran it. Check `is_player` first. |
| `.level` | The world it ran in, or `None` (the console has no world). |
| `.tell(message)` | Sends feedback - a string or a [`text(...)`](text.md) component. |

Anything the handler raises (or a `fail(...)`) is reported to the log and the command reports an
error, rather than taking effect half-way.
