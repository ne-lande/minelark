# Networking API Reference

The **`net`** namespace lets a pack's **server** and **client** scripts talk to each other over named
channels. It is the one path that can carry a server's own "events" out to a client - the server
sends on a channel, and client scripts listening on that channel react.

Both sides use the same `net` namespace, but the send methods differ by phase: a server addresses a
particular player (or everyone), while a client only ever sends to the server it is connected to.
Messages carry any JSON-able value (dict, list, string, number, bool, `None`).

A channel is just a name both sides agree on. Pick something unlikely to clash, e.g. prefix it with
your pack name (`"mypack/open_gui"`). Channel names may use letters, digits, and `_ . / : -`.

!!! note
    Messages only flow while a player is connected. Send from a callback (an event, a tick, a
    command), not at the top level of a script. On a single-player world the "server" and "client"
    are the same game, but the channel still works exactly the same way.

## Server scripts

In `minelark/server/` scripts:

| Call | What it does |
|---|---|
| `net.send(player, channel, data)` | Sends `data` to one player (a player like `ctx.player`, or a uuid string). |
| `net.broadcast(channel, data)` | Sends `data` to every connected player. |
| `net.on(channel, handler)` | Reacts to messages clients send on `channel`. |

A server `net.on` handler gets a `ctx` with:

- `ctx.channel` - the channel the message arrived on,
- `ctx.data` - the decoded value the client sent,
- `ctx.player` - the player who sent it (a `ctx.player`-style view).

```python
# Give a reward when a client asks for it, and tell that client it worked.
def on_claim(ctx):
    ctx.player.give("minecraft:diamond", 1)
    net.send(ctx.player, "mypack/claimed", {"ok": True})

net.on("mypack/claim", on_claim)

# Push an announcement to everyone when the server starts.
def on_started(ctx):
    net.broadcast("mypack/motd", "Welcome to the server!")

events.minelark.SERVER_STARTED.on(on_started)
```

## Client scripts

In `minelark/client/` scripts:

| Call | What it does |
|---|---|
| `net.send(channel, data)` | Sends `data` to the server. |
| `net.on(channel, handler)` | Reacts to messages the server sends on `channel`. |

A client `net.on` handler gets a `ctx` with `ctx.channel` and `ctx.data` (there is no `ctx.player` -
the message came from the server).

```python
# Ask the server to claim a reward, and show the reply locally.
def on_reply(ctx):
    if ctx.data["ok"]:
        client.show_message(text("Reward claimed!").color("green"))

net.on("mypack/claimed", on_reply)

def on_tick(ctx):
    # (send from your own input handling; shown here for shape only)
    net.send("mypack/claim", {})
```

*The usual Starlark builtins (`print`, `len`, `range`, and so on) work here too. See
[The Starlark Language](../language.md).*
