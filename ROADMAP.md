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

## Shipped since 1.0

The sandbox-native work that KubeJS structurally cannot match. The MC-agnostic cores are Tier-1
tested; the console is verified live and N3's server side is smoke-verified headless, while N3's
client-side path (HUD/consent) still wants a hand pass in a real client, as client-only features
always do:

- **N1 - In-game console.** An operator-gated Starlark REPL against the running game
  (`/minelark eval`), plus an opt-in loopback web console (JDK `HttpServer`, bound to `127.0.0.1`,
  token-guarded) with syntax highlighting and annotation-driven autocomplete. Off by default.
- **N2 - Developer tooling.** Console autocomplete served from the same `@StarlarkMethod` metadata
  that generates the docs (`/symbols`), a generated `minelark.pyi` type stub, a prelude/stdlib
  (`require`/`clamp`/`lerp`/`rgb`), and a copy-pasteable extension-API guide.
- **N3 - Server-to-client script propagation (the flagship).** A server pushes sandboxed client
  scripts to consenting players. The client owns the trust: an editable capability policy with a
  secure visual-only default, an integrity-checked offer-then-request transport (per-file SHA-256),
  and hot-push on `/minelark reload`. Off by default, never active in singleplayer.

## What's next

The KubeJS-parity core and the sandbox-native flagships have all shipped. What remains is reach - not
core capability.

### Multiloader

Extract a `Platform` service and ship **Fabric + NeoForge** (Architectury has no Forge on the 1.21
line, so NeoForge is the second loader). The `script` core stays shared and unchanged; a CI matrix
covers both.

### JEI / REI integration

Recipe-viewer hooks for scripted content. Needs third-party soft deps and cannot be unit-tested, so
it trails the sandbox-native work. The last parity remnant.

## Not planned (and why)

Deliberate non-goals. Each was considered and declined, and the reasoning is recorded so it is not
re-litigated.

- **No IDE / editor extension.** A dedicated extension or language server would make external `.star`
  completion seamless, but the console autocomplete already covers live editing and the `.pyi` stub is
  enough as a reference. Not worth the maintenance; the console is the completion story.
- **No ImGui for the console UI.** ImGui (`imgui-java`) drags in native libraries, and native
  dependencies are a cost Minelark is otherwise free of: a prebuilt binary per OS and architecture,
  a heavier jar and build, and a whole class of load failures in environments the binaries were not
  built for - a steep price for a developer-only console. A Minecraft-native `Screen` was also weighed
  and judged too limited. The chosen loopback web console gives a richer UI while keeping Minelark
  pure-JVM, and uniquely serves dedicated-server admins (over an SSH port-forward).
- **No JIT compilation.** The interpreter is not the bottleneck (scripts do registration and event
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
