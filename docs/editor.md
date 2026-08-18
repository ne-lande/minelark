# Editor setup

Minelark generates a Python stub of the scripting API, **[`minelark.pyi`](minelark.pyi)**, from the
same annotations as this reference - so it never drifts. It lists every documented namespace, method,
and parameter as Python types.

## Live completion: the console

The [web console](getting-started.md#web-console) autocompletes namespaces and their methods with no
setup at all. While you are iterating on a pack, that is the quickest path - type `registry.` and it
lists the methods, with signatures.

## The stub file

`minelark.pyi` is useful as a single-file, always-current **reference** of the API surface (readable
and grep-able), and as the basis for future editor tooling.

Be aware of one limitation, though. Minelark injects its namespaces (`storage`, `recipes`, `hud`, ...)
as globals - a `.star` file never `import`s them - and a Python language server won't surface globals
it can't see an import for. So dropping the stub next to your scripts will not, on its own, light up
completion in a `.star` file. The [console](getting-started.md#live-console) is the live-completion
story; the stub is your reference.

## What it covers

The stub covers the annotated namespaces: `log`, the startup builtins (`item`, `block`, `fluid`),
`recipes`, `tags`, `loot`, `datapack`, `storage`, `hud`, `mods`, and `registry`. The event, command,
text, and client / networking namespaces are documented by hand and are not in the stub yet.
