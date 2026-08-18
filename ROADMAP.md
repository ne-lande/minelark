# Minelark Roadmap

Minelark is **KubeJS-style scripting for Minecraft, written in Starlark** (a small, Python-like
language). Target: **Fabric, Minecraft 1.21.1, JDK 21**.

Where KubeJS hands scripts arbitrary Java (`Java.loadClass`), Minelark gives them a **curated, typed
API inside a real sandbox** - a script cannot reach outside what Minelark exposes. That is the whole
strategy: the sandbox is not a limitation to apologise for, it is what lets Minelark do things KubeJS
cannot do safely - a live in-game console, server-pushed client scripts. Safe to run untrusted,
provable in tests, approachable in Python-family syntax.

## Guiding principles

1. **MC-agnostic core.** The interpreter and API layer (`ru.nelande.minelark.script`) never import
   Minecraft; a thin adapter touches the game. Keeps the engine unit-testable and the door open for
   multiloader.
2. **Curated, sandboxed API only.** No arbitrary Java or reflection from scripts. Some KubeJS scripts
   will not port 1:1 - that is the deliberate trade, and it is what makes the sandbox real.
3. **Prove it without launching Minecraft.** Every behaviour ships with a test (three tiers below).
   Client-only rendering and networking are the exception, verified by hand in a real client.

## Shipped (v1.0)

The KubeJS-parity core is done, and where a client was needed it has been verified live:

- **Engine:** startup / server / client phases, `load()` module imports, `/minelark reload`, the
  `log` namespace.
- **Content:** items, blocks, fluids, tools and armor, block shapes, sound groups, creative tabs,
  runtime display names, and generated models/textures (a client resource pack) - all under
  `minelark:`.
- **Recipes:** shaped, shapeless, smelting/blasting/smoking, campfire, stonecutting, smithing, plus
  remove-by-filter, served through a generated data pack.
- **Tags, loot, data:** the `tags` namespace, block and entity loot, loot injection, and arbitrary
  `datapack.json`.
- **Events and runtime:** namespaced, typed, cancellable events (server and client) with a mutable
  `ctx`; `text()` / `translate()` components with colour, hover, and click; player / level / item /
  entity wrappers; custom `commands`.
- **Client:** client scripts, F3 `debug` lines, an on-screen `hud` (text, rectangles, progress bars,
  textures, item icons, pie charts, bar graphs), and tooltip / chat hooks.
- **Storage:** persistent `storage` (install-global), `world` (per-world), and
  `storage.player(uuid)` (per-world, per-player).
- **Networking:** `net` server-to-client and client-to-server channels carrying JSON, including
  reliable send-on-join.
- **Interop:** `mods` and `registry` discovery, and a content-type extension API for addon mods.
- **Docs:** an API reference auto-generated from annotations, published as a MkDocs site.

## What's next

The parity race is essentially run. From here the roadmap is about what the sandbox uniquely enables,
plus the polish that makes Minelark pleasant to build on.

### N1 - In-game console (live, safe eval)

An operator-gated REPL that evaluates Starlark against the running game, keeping a **live, unfrozen
module** so definitions persist between lines. This is a loaded gun in KubeJS (an eval that can
`Java.loadClass`); in Minelark it is just a REPL against a sandbox, so it is safe to ship.

- ✅ **Engine + command frontend.** `ConsoleSession` (MC-agnostic, Tier-1 tested) plus
  `/minelark eval <code>`: state persists across lines, `print()` and trailing expressions are
  echoed, errors are caught. Sees the read/inspect surface (`log`, `storage`, `world`, `mods`,
  `registry`, `text`). Verified live on a headless server.
- ✅ **Web console.** An opt-in loopback HTTP server (JDK built-in - no new dependency) serves a
  browser code editor (multiline, history, Ctrl+Enter) that evaluates against the same session on the
  server thread. A **developer feature, off by default** (`enabled` master switch); when on, an op
  runs `/minelark console` to start it **on demand** and gets a clickable, token-carrying link
  (`/minelark console stop` to close; `auto_start` for always-on). Bound to `127.0.0.1`,
  token-guarded, sandboxed Starlark; remote admins forward the port over SSH. `ConsoleServer` is
  MC-agnostic and Tier-1 tested over real HTTP; verified end-to-end against a live server. See
  `docs/getting-started.md`.
- ✅ **Editor polish.** The web editor has Starlark **syntax highlighting** (hand-rolled, still
  dependency-free), **Tab**-to-indent / **Shift+Tab** dedent, history persisted across reloads
  (`localStorage`), and a clear control.
- ✅ **Autocomplete.** Completion of top-level names and namespace members, driven by a manifest
  reflected from the same `@StarlarkMethod` annotations as the docs (served at `/symbols`) - so
  completion can never drift from the API. This is the first slice of the N2 tooling work.

### N2 - Developer experience and tooling

Make Minelark the nicest KubeJS-alike to build on.

- ✅ **Console autocomplete from the annotations.** A symbol manifest (`ConsoleSymbols`) reflected
  from the same `@StarlarkMethod` metadata that generates the docs, served at `/symbols` and consumed
  by the web editor - completion can never drift from the API. Tier-1 tested.
- ✅ **Python stub artifact.** `DocGenerator` also emits `docs/minelark.pyi` from the same `Phase`
  table (guarded by `checkApiDocs`, so it can't drift) - the whole documented API as Python types,
  published as a downloadable and documented under "Editor setup". Honest limit: because Minelark
  injects namespaces as globals (a `.star` file never `import`s them), a plain stub does not light up
  completion in an external editor on its own.
- **Not planned: an IDE/editor extension.** A dedicated extension (or language server) would make
  external `.star` completion seamless, but the console autocomplete already covers live editing and
  the `.pyi` is enough as a reference, so it is not worth the maintenance. The console is the
  completion story.
- ✅ **Prelude / standard library.** `PreludeApi` adds top-level helpers in **every** phase and the
  console: `require(condition, message)` (a top-level guard - Starlark forbids a bare top-level `if`),
  `clamp`, `lerp`, and `rgb`. Being `@StarlarkMethod`s, they flow automatically into the docs, the
  console autocomplete, and the `.pyi` stub. Tier-1 tested (`PreludeApiTest`); verified live.
- ✅ **Extension-API guide.** `docs/extending.md` is a complete, copy-pasteable worked example of an
  addon registering content types (sound groups, tool tiers, armor aliases, custom shapes) via the
  `minelark:types` entrypoint, checked against the 1.21.1 API. Growing the entrypoint further
  (mod-registered events / namespaces) is possible future work.

### N3 - Server-to-client script propagation (flagship)

Let a server ship client-side behaviour - HUDs, tooltips, client events - to connecting clients that
only have Minelark installed. "Server resource packs, but for behaviour." **This is structurally
impossible for KubeJS to do safely** (server-authored Java on every client is remote code execution);
Minelark can, because the sandbox means a pushed script cannot escape it. The security model is the
design work, not an afterthought:

- **Consent, like resource packs** - the client prompts ("this server wants to run client scripts")
  and remembers the choice per server.
- **A reduced capability set** for pushed scripts (a pushed script is trusted less than a locally
  installed one), described by a manifest the player can see and approve.
- **Hot-push** on `/minelark reload`, so server-driven client behaviour updates with no client action.

Highest ceiling of anything here; a post-1.0 flagship, built once the consent and capability model is
settled.

### Multiloader

Extract a `Platform` service and ship **Fabric + NeoForge** (Architectury has no Forge on the 1.21
line, so NeoForge is the second loader). The `script` core stays shared and unchanged; a CI matrix
covers both.

### Parity remnant

- **JEI / REI integration** - recipe-viewer hooks for scripted content. Needs third-party soft deps
  and cannot be unit-tested, so it trails the sandbox-native work.

### Not planned: JIT compilation

Considered and declined. The interpreter is not the bottleneck (scripts do registration and event
handling; the game loop dominates), the JVM already JITs the interpreter's hot paths, and a
Starlark-to-bytecode compiler is a research-grade effort with no user-visible payoff. If profiling
ever demands it, the cheap win is caching the parsed program across reloads - not a JIT.

## Testing strategy (three tiers)

- **Tier 1 - interop unit tests (no MC).** Real scripts through the real interpreter; assert the
  collected declarations and dispatched callbacks.
- **Tier 2 - pure-logic tests (no MC).** Recipe JSON, ingredient matching, tag math.
- **Tier 3 - headless server / gametest.** In-world checks; client-only rendering and networking are
  verified by hand in a real client.

Goal: every behaviour has a Tier-1 or Tier-2 test, so both parity and new features are provable
without launching Minecraft.

## CI/CD

- **CI:** JDK 21, `./gradlew build` (compile, all tests, and the doc-drift check) on push and PR.
- **Docs:** the MkDocs site publishes to GitHub Pages on push to `main`.
- **Release:** a `v*` tag builds with the tag version and attaches the jar to a GitHub Release.
