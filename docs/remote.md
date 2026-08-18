# Pushed client scripts

A Minelark server can send client-side scripts to the players who connect to it, and those scripts
run on each player's own machine: HUDs, tooltips, F3 lines, client events. Think of it as resource
packs, but for behaviour instead of textures. A player only needs Minelark installed; they do not need
the server's script files.

This is safe because a pushed script runs in the same Starlark sandbox as any other client script. It
cannot load arbitrary Java, touch the filesystem, or reach another server. On top of that the client,
not the server, decides what a pushed script is allowed to do.

Two rules frame the whole feature:

- It never runs in singleplayer or on a world you host over LAN. It is only for connecting to a
  separate server.
- The client is in charge. A server can ask for capabilities; the player's own policy decides what is
  granted, and the player is asked before anything runs.

## Turning it on (server)

It is off by default. In `minelark/config.json`, set:

```json
{
  "remote_scripts": {
    "enabled": true
  }
}
```

While it is off the server never scans the push folder and never offers anything, so leave it off
unless you actually want to ship client behaviour.

## Writing a pushed script (server)

Pushed scripts live in `minelark/push/`. They are client-phase scripts, so they use the same API as
the scripts in `minelark/client/` (`events`, `hud`, `debug`, `text`, and so on).

A file is only offered if its first lines carry the opt-in directive:

```python
# minelark: push

def on_tick(ctx):
    hud.text("greeting", text("Hello from the server").color("aqua"), x = 4, y = 4)

events.minelark.CLIENT_TICK.on(on_tick)
```

The directive is a deliberate switch, so a work-in-progress file sitting in the folder is never sent
by accident. A top-level file without it is ignored. Helper files you pull in with `load()` should
live in a subfolder (for example `push/lib/helpers.star`); files in subfolders are always shipped so
those imports resolve, but they never run on their own.

By default a pushed script is granted the visual set only. If it needs more, name the capabilities on
the directive:

```python
# minelark: push capabilities=hud,net
```

The capabilities a bundle asks for are the union of what its entry files declare.

Changes hot-push. Run `/minelark reload` and connected players who already consented pick up the new
scripts with no action on their side. Emptying the folder (or removing the directives) withdraws them.

There is a size limit: at most 64 files totalling 1 MB. A larger push folder is refused and nothing is
offered (you will see a line in the server log).

## Capabilities

| Token      | What it allows                                                        | Default |
|------------|-----------------------------------------------------------------------|---------|
| `hud`      | Draw on-screen HUD graphics (`hud` namespace)                         | granted |
| `debug`    | Add F3 debug-overlay lines (`debug` namespace)                        | granted |
| `client`   | Read the local player and world, show local messages                 | granted |
| `mods`     | Discover loaded mods (`mods` namespace)                               | granted |
| `registry` | Query the registries (`registry` namespace)                          | granted |
| `net`      | Exchange JSON with the server over channels (`net` namespace)         | withheld |
| `chat`     | Send chat and run commands as the player (`client.send_chat`)         | withheld |

The `events`, `text`, and `log` namespaces and the prelude helpers are always present; they are local
and harmless.

`net` and `chat` are withheld by default. `chat` in particular would let a server run commands as you,
so it is never granted unless you choose to. A live, data-driven HUD needs `net`, which is why a server
that wants one has to request it and the player has to allow it.

## Consenting and your policy (client)

The first time a server offers scripts, you get a prompt in chat:

```
[Minelark] This server wants to run client scripts on your machine.  [Accept] [Decline] [What can they do?]
```

- `[Accept]` runs the scripts and remembers the choice for that server.
- `[Decline]` ignores them and remembers that too.
- `[What can they do?]` explains the sandbox and what this server asked for.

You can also type the commands directly. They are client-side only:

- `/mlremote accept` and `/mlremote decline` answer a pending prompt.
- `/mlremote trust` marks the current server as always-allowed (no more prompts from it).
- `/mlremote details` prints what pushed scripts can do and what this server requested.
- `/mlremote list` shows your current policy.

Your decisions and preferences live in `minelark/remote_policy.json`, which you can edit by hand:

```json
{
  "enabled": true,
  "default_allow": ["hud", "debug", "client", "mods", "registry"],
  "trusted_sources": ["192.168.0.10", "play.example.com"],
  "blocked_sources": [],
  "decisions": {
    "play.example.com": "accepted"
  }
}
```

- `enabled`: set to `false` to turn the whole feature off on your side. No server can then run scripts
  on your client.
- `default_allow`: the capabilities you are willing to grant. This is the ceiling. Even if a server
  asks for `net`, a pushed script only gets it if `net` is in this list. Add `net` here if you want
  live server HUDs, and `chat` only if you really trust where you play.
- `trusted_sources`: addresses that skip the prompt and run straight away.
- `blocked_sources`: addresses whose offers are always ignored.
- `decisions`: the per-server accept/decline choices the prompt records for you.

## Integrity and caching

The offer a server sends is a manifest: the file names, a SHA-256 hash of each, and the requested
capabilities. Nothing runs from the manifest alone. When you consent, the client asks for the file
bodies it does not already have, and checks each one against its hash before writing or running it. A
body that does not match is dropped.

Downloaded files are cached under `minelark/.received/<server>/`, so reconnecting does not re-download
anything that has not changed, and a reload only fetches the files that actually changed.
