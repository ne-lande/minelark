# Events API Reference

The `events` namespace (server scripts) runs your code when something happens in game. Events are
grouped by namespace, the same way Minecraft ids are, so two mods can define an event with the same
short name without stepping on each other:

```python
events.minelark.SERVER_STARTED.on(handler)   # Minelark's own events
events.somemod.SOME_EVENT.on(handler)         # another mod's events, once it integrates
```

The event names are typed constants. If you misspell one (`events.minelark.SEVER_STARTED`) you get
an error immediately, rather than a handler that silently never fires.

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

`ctx` tells you what happened. For now that's just the event id:

| Field | Value |
|---|---|
| `ctx.event` | The full id of the event that fired, e.g. `minelark:server_started`. |

Events that carry more (a player, a position, a way to cancel) will add fields to `ctx` as they land.

## Looking up an event by id

If an event isn't exposed as a named constant, for example one that comes from another mod, grab it
by its full id:

```python
events.of("minelark:server_started").on(on_started)
events.of("somemod:custom_event").on(...)
```

## Available events

| Event | When it fires |
|---|---|
| `events.minelark.SERVER_STARTED` | Once, after the server or world finishes loading. |

More events (player join, block break, and so on) are on the [roadmap](../roadmap.md).
