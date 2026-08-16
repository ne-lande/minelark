# Minelark Documentation

Minelark lets you change Minecraft with Starlark scripts, without writing Java or rebuilding a mod.

- [Getting Started](getting-started.md): where scripts go, the phases, and your first item.
- [The Starlark Language](language.md): a short primer if you haven't used Starlark.
- API reference:
  - [Common](api/common.md): the `log` namespace, available everywhere.
  - [Startup](api/startup.md): registering content (`item`, `block`).
  - [Server](api/server.md): reloadable data (`recipes`).
  - [Events](api/events.md): reacting to what happens in game.

Minelark is early days. The API grows milestone by milestone, and these docs track what actually
works right now rather than what's planned.

The API reference pages under `api/` (except events) are generated straight from the code, from the
`@StarlarkMethod` annotations, so they can't drift out of sync. The guide pages and the events
reference are written by hand.
