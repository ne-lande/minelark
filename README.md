# Minelark

Minelark is a scripting mod for Minecraft. It lets you add and change content with
[Starlark](https://github.com/bazelbuild/starlark) scripts instead of writing Java, in the same
spirit as KubeJS but using Starlark (a small Python dialect) rather than JavaScript. The name is
just Minecraft + Starlark.

Fabric, Minecraft 1.21.1, Java 21.

## What it does

Put `.star` files in your instance's `minelark/` folder and they run at the right time. Scripts are
split into phases:

```
<instance>/minelark/
├── startup/   # before registries freeze: items, blocks
├── server/    # on world load and /minelark reload: recipes, events
└── client/    # on client start (nothing built-in yet)
```

A startup script looks like this:

```python
print("Hello from Starlark!")

item("ruby", max_stack_size = 64)
item("sapphire", max_stack_size = 16)
```

Because Starlark is sandboxed, scripts can't reach arbitrary Java the way KubeJS can with
`Java.loadClass`. Minelark gives you a specific API instead. That's a deliberate trade: less raw
power, but packs stay predictable and can't wander off into the rest of the JVM.

## Documentation

If you're writing scripts, start with:

- [Getting Started](docs/getting-started.md): the folders, the phases, your first item.
- [The Starlark Language](docs/language.md): a short primer if Starlark is new to you.
- [Startup API Reference](docs/api/startup.md): the item and block options.

The full index is at [docs/](docs/README.md). The docs are also published as a website (Material for
MkDocs) on every push to `main`. To preview locally:

```sh
cp ROADMAP.md docs/roadmap.md   # the site pulls in the roadmap, which lives at the repo root
mkdocs serve                    # http://127.0.0.1:8000 (mkdocs is in the Nix dev shell)
```

## Building

You need JDK 21. There's a Nix dev shell (`nix develop`, or direnv loads it for you); otherwise just
install a JDK.

```sh
./gradlew build      # compile, run the tests, build build/libs/minelark-<version>.jar
./gradlew runClient  # launch a dev client
./gradlew runServer  # launch a dev server (needs run/eula.txt with eula=true)
```

## Status

Early, but usable. Items, blocks, tags, recipes, block drops, and events all work today, with live
reload for server scripts. See [ROADMAP.md](ROADMAP.md) for what's done and what's next.

## Licence

Copyright © 2026 nelande.

Minelark is licensed under the GNU Lesser General Public License v3.0 or later (LGPL-3.0-or-later).
You can use it in your own packs and mods however you like. If you distribute a changed version of
Minelark itself, those changes have to stay open under the same license, with the original credits
intact. The full text is in [LICENSE](LICENSE), alongside [LICENSE.GPL](LICENSE.GPL), which the LGPL
builds on.
