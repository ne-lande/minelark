# The Starlark Language

Minelark scripts are written in [Starlark](https://github.com/bazelbuild/starlark), a small dialect
of Python that started life as a configuration language for Bazel. If you've written Python you
already know most of it. The one big difference from KubeJS is that Starlark is sandboxed: it leaves
out anything that could hang the game or reach outside the script, which also means packs are
deterministic and safe to load.

## Syntax

It looks like Python:

```python
name = "ruby"
count = 64
enabled = True          # True / False / None are capitalised
tags = ["ore", "gem"]   # list
props = {"hardness": 3} # dict

def greet(who):
    return "Hello, " + who

if count > 32:
    print("big stack")
else:
    print("small stack")

for t in tags:
    print(t)
```

You get the usual builtins: `print`, `len`, `range`, `enumerate`, `zip`, `sorted`, `min`, `max`,
`dict`, `list`, `str`, `int`, `bool`, `type`, plus the string, list, and dict methods you'd expect
(`"x".upper()`, `[].append(x)`, `{}.get(k)`).

## What's missing

Starlark leaves some things out on purpose:

| Restriction | What to do instead |
|---|---|
| No `while` loops | Loop with `for` over a finite sequence, like `for i in range(n)`. Scripts always terminate. |
| No recursion | A function can't call itself. Rewrite it as a loop. |
| No arbitrary Java | There's no `Java.loadClass` like KubeJS has. You work through Minelark's API. (You can still import other `.star` files with [`load()`](getting-started.md#sharing-code-with-load).) |
| No file, network, clock, or random I/O | Scripts are pure. If a capability is ever needed, Minelark exposes it explicitly. |
| Modules freeze after they run | Once a script finishes, its globals are read-only. You can't stash mutable state in a global and change it later. |

The freezing rule sounds worse than it is. You declare content at the top level, and for events you
hand callbacks to Minelark. It calls them later with a fresh `ctx`, so anything you change goes
through the API rather than through a shared global.

## How values map to the API

- Strings stay strings.
- Integers and floats are passed through; Minelark converts them where a method wants one or the
  other.
- `True` / `False` are booleans, `None` means "leave it at the default".
- Lists and dicts are used where a specific function documents them.

## When something goes wrong

A syntax error or a bad call (say, an invalid item id) gets logged with the name of the script it
came from, and the rest of your scripts keep running. Look for log lines prefixed with your file
name.

## Further reading

The [Starlark spec](https://github.com/bazelbuild/starlark/blob/master/spec.md) is the full
language reference. Minelark uses the Java implementation (`net.starlark.java`).
