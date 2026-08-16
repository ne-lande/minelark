# Text API Reference

Anywhere a message goes out (right now that is event callbacks in server scripts), you can send
either a plain string or a styled text component. Build components with the `text(...)` and
`translate(...)` builtins, then chain style and interactivity onto them.

```python
def on_join(ctx):
    ctx.player.tell(
        text("Welcome, ").append(text(ctx.player.name).color("gold").bold()))

events.minelark.PLAYER_JOINED.on(on_join)
```

`ctx.player.tell(...)` accepts a plain string too, so `ctx.player.tell("hi")` is the same as
`ctx.player.tell(text("hi"))`.

## Building a component

| Builtin | What it makes |
|---|---|
| `text(content)` | A literal piece of text. |
| `translate(key, args = [])` | A component from a translation key, resolved on each player's own client. `args` fill the key's placeholders and may be strings or other components. |

```python
text("Hello")
translate("block.minecraft.stone")
translate("chat.type.text", [ctx.player.name, "hi there"])
```

## Styling

Every method returns a new component, so components are safe to build once and reuse; chaining never
changes the original.

| Method | Effect |
|---|---|
| `.color(value)` | A vanilla colour name (`"gold"`, `"dark_red"`, `"aqua"`, ...) or `#rrggbb` hex. |
| `.bold(on = True)` | Bold. Pass `False` to force it off. |
| `.italic(on = True)` | Italic. |
| `.underline(on = True)` | Underlined. |
| `.strikethrough(on = True)` | Struck through. |
| `.obfuscated(on = True)` | The scrambled, glitchy effect. |

A bad colour name is reported as an error, so typos surface right away.

## Interactivity

| Method | On click / hover |
|---|---|
| `.hover(text)` | Shows tooltip text on hover (a string or another component). |
| `.click_run(command)` | Runs a command as the clicking player, e.g. `"/spawn"`. |
| `.click_suggest(text)` | Drops text into the player's chat box. |
| `.click_url(url)` | Opens a URL (the client asks the player to confirm). |
| `.click_copy(text)` | Copies text to the player's clipboard. |

## Joining pieces

`.append(text)` adds another component (or string) after this one:

```python
line = (text("[").color("gray")
        .append(text("INFO").color("green"))
        .append(text("] ").color("gray"))
        .append("server is up"))
```

The appended pieces keep their own styling; the parent's style applies to anything without its own.
