# Events API Reference

The `events` namespace runs your code when something happens in game. It is available to both
server scripts (`minelark/server/`) and client scripts (`minelark/client/`). Server events cover the
world and its players; client events fire on a single player's own machine (tooltips, incoming chat,
the client tick). Events are grouped by namespace, the same way Minecraft ids are, so two mods can
define an event with the same short name without stepping on each other:

```python
events.minelark.SERVER_STARTED.on(handler)   # Minelark's own events
events.somemod.SOME_EVENT.on(handler)         # another mod's events, once it integrates
```

The event names are typed constants. If you misspell one (`events.minelark.SEVER_STARTED`) you get
an error immediately, rather than a handler that silently never fires. The same applies if you reach
for an event from the wrong phase - a server script asking for a client event (or the reverse) is
rejected on the spot, because that handler could never fire (a client can't observe server events
without networking, which Minelark doesn't do yet).

## Subscribing

An event holds a list of listeners. A listener is a plain function that takes the event context,
`ctx`:

```python
def on_started(ctx):
    log.info("The world is ready! (" + ctx.event + ")")

events.minelark.SERVER_STARTED.on(on_started)
```

| Method | What it does |
|---|---|
| `EVENT.on(fn)` | Subscribes `fn`. `EVENT.add(fn)` is the same thing. |
| `EVENT.remove(fn)` | Removes a listener you added earlier (matched by identity). Returns whether one was removed. |
| `EVENT.list()` | The listeners currently subscribed. |

Keep in mind that modules freeze after they load and each run starts clean, so `remove()` and
`list()` are mainly useful within a single run or for a quick look, not as state that survives a
reload.

## The `ctx` object

`ctx` is a small event object. It always has these fields:

| Field | Value |
|---|---|
| `ctx.event` | The full id of the event that fired, e.g. `minelark:player_chat`. |
| `ctx.cancellable` | Whether this event can be cancelled. |
| `ctx.cancelled` | Whether it has been cancelled so far. |

Beyond those, each event adds its own fields (see the table below): a chat event has `ctx.message`,
a block-break event has `ctx.player`, `ctx.block`, and `ctx.x` / `ctx.y` / `ctx.z`, and so on. Read
a field that an event doesn't have and you get an error naming the field, so typos surface straight
away.

### Cancelling

If an event is cancellable, stop the default action with `ctx.cancel()`:

```python
def no_bedrock_breaking(ctx):
    if ctx.block == "minecraft:bedrock":
        ctx.cancel()

events.minelark.BLOCK_BROKEN.on(no_bedrock_breaking)
```

`ctx.cancel()` on an event that can't be cancelled raises an error rather than failing quietly.
`ctx.cancelled = True` does the same thing as `ctx.cancel()`.

### Editing fields

A few events expose an editable field. Reassign it to change what happens. For chat, rewrite the
line before it goes out:

```python
def shout(ctx):
    ctx.message = ctx.message.upper()

events.minelark.PLAYER_CHAT.on(shout)
```

Only the fields marked editable below can be reassigned. Assigning to any other field raises an error.

### Wrappers

Some `ctx` fields are small typed views of game objects rather than plain values.

**`ctx.player`** - a player:

| Member | Value |
|---|---|
| `.name` | The player's display name. |
| `.uuid` | The player's UUID, as a string. |
| `.x` / `.y` / `.z` | The player's position. |
| `.health` | Current health (2 per heart). |
| `.held_item` | The item in the main hand (an item view, below). |
| `.level` | The world the player is in (a level view, below). |
| `.tell(message)` | Sends a message (a string or a `text(...)` component - see the [Text API](text.md)). |
| `.give(item, count = 1)` | Gives the player an item by id (unknown ids are ignored). |
| `.teleport(x, y, z)` | Moves the player within their current world. |

**`ctx.level`** (and `ctx.player.level`) - a world:

| Member | Value |
|---|---|
| `.dimension` | The dimension id, e.g. `minecraft:overworld`. |
| `.time` | Time of day, in ticks. |
| `.is_day` / `.is_raining` | Current conditions. |

**`ctx.player.held_item`** (and other stacks) - an item stack:

| Member | Value |
|---|---|
| `.id` | The item id. |
| `.count` | How many are in the stack. |
| `.name` | The stack's display name. |
| `.is_empty` | Whether there's nothing there. |

**`ctx.attacker`** (on death) - a non-player entity:

| Member | Value |
|---|---|
| `.type` | The entity type id, e.g. `minecraft:creeper`. |
| `.uuid` | Its UUID, as a string. |
| `.name` | Its display name. |
| `.x` / `.y` / `.z` | Its position. |
| `.level` | The world it is in. |

```python
def greet(ctx):
    ctx.player.tell("Welcome, " + ctx.player.name + "!")
    if ctx.player.level.dimension == "minecraft:the_end":
        ctx.player.give("minecraft:elytra")

events.minelark.PLAYER_JOINED.on(greet)
```

## Looking up an event by id

If an event isn't exposed as a named constant, for example one that comes from another mod, grab it
by its full id:

```python
events.of("minelark:server_started").on(on_started)
events.of("somemod:custom_event").on(...)
```

## Available events

All under the `minelark` namespace. "Cancel" marks events you can stop with `ctx.cancel()`; "Edit"
lists the fields you can reassign.

### Server events

For scripts in `minelark/server/`.

| Event | When it fires | Extra `ctx` fields | Cancel | Edit |
|---|---|---|---|---|
| `SERVER_STARTED` | Once, after the server or world finishes loading. | - | no | - |
| `SERVER_TICK` | Every server tick (20 a second). Keep the work tiny. | - | no | - |
| `PLAYER_JOINED` | A player finishes joining. | `player` | no | - |
| `PLAYER_LEFT` | A player disconnects. | `player` | no | - |
| `PLAYER_DEATH` | A player is about to die. Cancel to keep them alive. | `player`, `source`, `amount`, `attacker` (if any) | yes | - |
| `PLAYER_CHAT` | A player sends a chat message. | `player`, `message` | yes | `message` |
| `BLOCK_BROKEN` | Just before a player breaks a block. | `player`, `block`, `x`, `y`, `z`, `level` | yes | - |
| `BLOCK_PLACED` | Just before a player places a block. | `player`, `block`, `x`, `y`, `z`, `level` | yes | - |
| `COMMAND` | Before a command runs. | `player` (absent for the console), `command` | yes | `command` |
| `EXPLOSION` | An explosion goes off (notification). | `x`, `y`, `z`, `power`, `level` | no | - |

### Client events

For scripts in `minelark/client/`. These run on the player's own machine, so they only ever fire in
single-player or on the client of a multiplayer session - never on a dedicated server.

| Event | When it fires | Extra `ctx` fields | Cancel | Edit |
|---|---|---|---|---|
| `CLIENT_STARTED` | Once, after the game client finishes starting. | - | no | - |
| `CLIENT_STOPPING` | Once, as the client shuts down. | - | no | - |
| `CLIENT_TICK` | Every client tick (20 a second). Keep the work tiny. | - | no | - |
| `ITEM_TOOLTIP` | While an item's tooltip is built. | `item`, `lines` | no | `lines` |
| `CLIENT_CHAT_RECEIVED` | A chat or system message arrives. Cancel to hide it. | `message` | yes | `message` (system messages only) |
| `CLIENT_CHAT_SENT` | Just before you send a chat message. Cancel to stop it. | `message` | yes | `message` |

`ITEM_TOOLTIP` is how you change tooltips. `ctx.lines` is the list of lines the tooltip will show,
each a string or a `text(...)` component. Reassign it to add, remove, or reorder lines; leave it
alone and the tooltip is untouched:

```python
def show_id(ctx):
    ctx.lines = ctx.lines + [text("id: " + ctx.item.id).color("dark_gray")]

events.minelark.ITEM_TOOLTIP.on(show_id)
```

Editing incoming chat only works for system and game messages. A signed player message can be hidden
(`ctx.cancel()`) but not rewritten, so reassigning `ctx.message` on one is ignored - the same signing
limitation the server-side `PLAYER_CHAT` has.

A note on chat editing: chat messages are signed, so a rewritten `ctx.message` can't be slipped back
into the original line. Minelark handles it by cancelling the original and re-sending your edited text
as a server message, which means the fancy formatting and the signature are dropped. Cancelling chat
outright has no such caveat.

More events are on the [roadmap](../roadmap.md).
