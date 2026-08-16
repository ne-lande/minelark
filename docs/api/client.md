# Client API Reference

Scripts in the **`minelark/client/`** folder run once when the game client starts, on the player's
own machine. They get the same [`events`](events.md) namespace as server scripts (the client events
are listed there), the [`text`](text.md) builtins, and two client-only namespaces described here:
`client` and `debug`.

Client scripts never run on a dedicated server, and they cannot see server events - a client can only
observe what happens on its own machine. Reach for a client script when you want to react to tooltips,
incoming chat, or the client tick, or to show something locally to the player.

## `client`

The `client` namespace reads the local player and world and lets you send chat or show local
messages. Everything is read live, so use it from a callback (a tick or an event), not at the top of
the file - when the file first runs there is no player yet, so `client.player` is `None`.

| Member | Value |
|---|---|
| `client.player` | The local player, or `None` if not in a world. Same view as `ctx.player`: `.name`, `.uuid`, `.x` / `.y` / `.z`, `.health`, `.held_item`, `.level`, `.tell(...)`. |
| `client.world` | The world the player is in, or `None`. `.dimension`, `.time`, `.is_day`, `.is_raining`. |
| `client.send_chat(message)` | Sends a chat message to the server as if you typed it. A message starting with `/` is sent as a command. |
| `client.show_message(message)` | Shows a message in your own chat locally. Nothing is sent to the server - only you see it. Takes a string or a `text(...)` component. |

On the client, `client.player.tell(...)` shows a local message (the same as `show_message`).
`.give(...)` and `.teleport(...)` are server-only and do nothing here.

```python
def on_tick(ctx):
    p = client.player
    if p and p.y < -60:
        client.show_message(text("You are falling into the void!").color("red"))

events.minelark.CLIENT_TICK.on(on_tick)
```

## `debug`

The `debug` namespace adds your own lines to the F3 debug overlay. Each line is keyed, so setting the
same key again replaces it - which is exactly what you want from a per-tick callback.

| Method | What it does |
|---|---|
| `debug.set(key, value)` | Adds or replaces a line, shown as `key: value`. Call `str(...)` on non-string values. |
| `debug.remove(key)` | Removes a line you added. Returns whether one was removed. |
| `debug.clear()` | Removes all lines added by scripts. |

```python
def on_tick(ctx):
    p = client.player
    if p:
        debug.set("pos", str(int(p.x)) + ", " + str(int(p.y)) + ", " + str(int(p.z)))
        debug.set("dim", p.level.dimension)

events.minelark.CLIENT_TICK.on(on_tick)
```

Your lines appear under the vanilla ones on the left side of the F3 screen.
