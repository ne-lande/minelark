# Minelark Roadmap

Minelark is a **KubeJS-style scripting bridge for Minecraft using the Starlark language**
(Minecraft + Starlark). Target: **Fabric, Minecraft 1.21.1, JDK 21**.

The north star is **feature parity with KubeJS**, delivered as shippable milestones. Every
behaviour is validated by JUnit tests, and everything ships through a CI/CD release pipeline.

## Guiding principles

1. **MC-agnostic core.** The interpreter and API-collection layer (`ru.nelande.minelark.script`)
   never import Minecraft. Only a thin adapter touches the game. This keeps the engine
   unit-testable without launching MC - and keeps the door open for multiloader (M8).
2. **Test everything.** Each feature ships with tests (see Testing Strategy). Parity is
   *provable* without launching Minecraft wherever possible.
3. **Sandbox-first interop.** Unlike KubeJS (which exposes arbitrary Java via
   `Java.loadClass`), Starlark is a deterministic sandbox. Minelark exposes a **curated,
   typed API only** - no arbitrary reflection. Safer packs; some KubeJS scripts won't port 1:1
   *by design*. This is the one deliberate exception to "rip all of KubeJS".

## Decisions (locked)

- **Sequencing:** Foundations (M0) first.
- **Release targets:** GitHub Releases only (Modrinth/CurseForge can be added later).
- **Interop model:** Curated API only.
- **Multiloader (M8):** Architectury has **no Forge support on 1.21** → path is **Fabric + NeoForge**.

## Milestones

### M0 - Foundations & CI/CD  ← current
- `git` repo + hygiene (README, LICENSE, `.gitattributes`, `.gitignore`).
- **CI** (GitHub Actions): build + test on every push/PR, Gradle cache.
- **Release pipeline**: tag `v*` → build → attach jar to a GitHub Release.
- Reusable `ScriptTest` harness; headless-server smoke test.
- *(CI has real network; the local dev proxy is sandbox-only and is not used in CI.)*

### M1 - Scripting engine core *(KubeJS: script system)*
- ✅ Server + client execution phases (startup, server, client all run).
- ✅ `load()` module imports (cached, cycle-detected, sandboxed to the scripts folder).
- ✅ `/minelark reload` re-runs server scripts (operator-only).
- ✅ `console.info/warn/error` tagged with script name + level.
- ✅ General engine + predeclared-globals framework (`ScriptEngine` + `StarlarkHost.environment`).
- ✅ **Persistent data storage**: three scoped key-value stores (server scripts), each with
  `set/get/has/delete/keys/clear` and values that are any JSON-able Starlark value, flushed on every
  change and surviving `/minelark reload` and restarts. **`storage`** is install-global
  (`<gamedir>/minelark/storage.json`); **`world`** is saved with the world (`<world>/minelark/world.json`,
  isolated between worlds); **`storage.player(uuid)`** is per-world and per-player
  (`<world>/minelark/players/<uuid>.json`). The `world`/player stores bind to the save on server start
  (one object reused across reloads, detached on stop). Tier-1 tested (`StorageTest`, incl. persistence
  and isolation round-trips) and validated live (a boot counter incremented across two server runs).

### Documentation *(cross-cutting)*
- Hand-written guide docs under `docs/` (getting started, language primer).
- ✅ API reference **auto-generated** from `@StarlarkMethod` annotations (`./gradlew generateApiDocs`);
  `checkApiDocs` runs in `check`/CI so docs can't drift from the code.
- ✅ Published as a **website** (Material for MkDocs) to **GitHub Pages** on every push to `main`
  (`.github/workflows/docs.yml`); built with `--strict` so broken links fail the build.

### M2 - Content registration *(KubeJS: startup registry)*
- ✅ Items: durability, rarity, fireproof, food (nutrition/saturation), fuel (burn time).
- ✅ Blocks: `block(...)` with hardness, resistance, light level, tool requirement (+ auto block item).
- ✅ Creative tabs: items → Ingredients, blocks → Building Blocks.
- ✅ `display_name` on items and blocks (runtime custom name - no language file needed).
- ✅ `tags` on items and blocks, applied via the generated datapack subsystem (below); verifiable
  in-game with `/minelark tags <id>`.
- ✅ **Model/texture generation**: a second generated pack - a **client resource pack**
  (`GeneratedResourcePack` + `AssetJson`, injected by the extended `ResourcePackManagerMixin`) writes
  models/blockstates so scripted content stops being missing-texture. Authors drop PNGs (or override
  JSON) into `minelark/assets/`. MC-free JSON is Tier-2 tested (`AssetJsonTest`).
- ✅ **Block sound groups**: `block(sound=...)` (curated set -> `BlockSoundGroup`).
- ✅ **Tools & armor**: `item(tool_type=, tool_tier=)` (pickaxe/axe/shovel/hoe/sword over the vanilla
  material tiers) and `item(armor_slot=, armor_material=)`; handheld models for tools.
- ✅ **Block shapes**: `block(shape="slab"|"stairs"|"fence"|"wall")` - real Slab/Stairs/Fence/Wall
  blocks with vanilla-accurate generated blockstates/models and slab double-drop loot.
- ✅ **Fluids**: `fluid(id, luminance=, tint=)` registers a still+flowing `FlowableFluid`, a fluid
  block, and a bucket, with a client fluid render handler.
- Spec collection is Tier-1 tested; the MC block/item/fluid/tool wiring is compile-validated and
  live loads-clean on a headless server (registration confirmed; in-world rendering needs a client).

### ✅ Generated pack subsystem *(shared infra - unblocks tags, recipes, loot)*
Minecraft has no runtime "add to tag / add recipe" API and Fabric only loads jar-bundled packs, so
script-driven **tags, recipes, and loot** all need Minelark to serve a generated data pack. Done:
`GeneratedDataPack` writes JSON to `<gamedir>/minelark/.generated/datapack/` and a `@ModifyVariable`
mixin (`ResourcePackManagerMixin`) injects a `DirectoryResourcePack` provider into the **server**
datapack manager (identified by its `VanillaDataPackProvider`). Recipes (M3) and loot (M4) will
write into the same pack.

### M3 - Recipes *(KubeJS: server recipes - headline feature)*
- ✅ Add: shaped, shapeless, smelting/blasting/smoking (via the `recipes` namespace in server
  scripts), reloadable with `/minelark reload` (regenerates the pack + `reloadResources`). Live-validated.
- ✅ Ingredient support: items and `#tags`; bare names default to `minecraft:`; result counts.
- ✅ **More recipe types**: `recipes.campfire`, `recipes.stonecutting`, `recipes.smithing`
  (smithing_transform) - same generated-datapack path, Tier-2 tested against the exact 1.21.1 JSON.
- ✅ **Remove/replace by filter**: `recipes.remove({id|mod|type|input|output})` (fields ANDed) drops
  matching recipes at load via a `RecipeManagerMixin` (rebuilds the immutable `recipesByType`/
  `recipesById` at `apply` TAIL, reading filters swapped on `/minelark reload`). Replace = remove + add.
  Filter matching is MC-agnostic + Tier-2 tested (`RemovalSpec`); removal is live-validated on a
  headless server (a vanilla recipe dropped, and re-dropped on reload).

### M4 - Tags · Loot · Data *(KubeJS: server data)*
- ✅ Item/block tags (M2). ✅ **Block loot**: `block(..., drops=...)` generates a block loot table
  (self-drop by default, `"none"`, or a specific item). Validated live.
- ✅ **Generic tags**: `tags.item/block/fluid/entity(tag, members)` - add any ids (vanilla, Minelark's,
  other mods') to any tag; merged with the `item()`/`block()` spec tags in the generated pack.
- ✅ **Entity drops**: `loot.entity_drops(entity, drops)` replaces an entity's loot table
  (`loot_table/entities/<id>.json`). **Loot injection**: `loot.inject(table, drops)` appends a pool to
  an existing table (chests, ...) via `LootTableEvents.MODIFY`. Drops = item id or
  `{item, count(=int or [min,max]), chance}`.
- ✅ **Generic datapack JSON**: `datapack.json(path, obj)` writes arbitrary JSON into the pack
  (Starlark value -> JSON), for anything unmodelled (advancements, predicates, ...).
- Spec/JSON building is Tier-1/2 tested (`M4Test`); the loot-inject event wiring is compile-validated
  and live loads-clean (tags applied + pack loads with no loot/tag errors; entity-drop firing needs
  `/summon`, broken headless).

### M5 - Events & runtime API *(KubeJS: events)*
- ✅ **Java→Starlark callback bridge**: `events.on(name, fn)` registers callbacks that Minelark
  invokes on a fresh `StarlarkThread`. First event: `server_started`. Validated live.
- ✅ More events: player (join/leave/death/chat), block break/place, server tick, command, explosion -
  each passing a **mutable `ctx`** to the callback. Cancellable events expose `ctx.cancel()` /
  `ctx.cancelled`; editable fields (chat `message`, `command`) are reassigned in place
  (`ctx.message = ...`), backed by Starlark's `Structure` setField. Fabric-API-backed events are
  wired directly; block-place/command/explosion have no Fabric event and use mixins
  (`BlockItemMixin`, `CommandManagerMixin`, `ExplosionMixin`). Tier-1 tested; `command` + reload
  validated live (headless summon is broken in the dev env, so block-place/explosion firing is
  compile-validated + loads-clean only).
- ✅ **Text/component API**: `text(content)` / `translate(key, args)` build an immutable `MineText`;
  chain `.color/.bold/.italic/.underline/.strikethrough/.obfuscated/.hover/.click_*/.append`.
  `ctx.player.tell(...)` takes a string or a component (adapter `toMcText`).
- ✅ **Wrappers**: `PlayerView` (name, uuid, x/y/z, health, held_item, level + tell/give/teleport),
  `LevelView`, `ItemStackView`, `EntityView` - all MC-agnostic. Wired: `ctx.level` on block/explosion,
  `ctx.attacker` on death, `ctx.player.held_item`/`.level`.
- ✅ **Richer custom commands**: `commands.register(name, handler, permission, args)` registers a
  `/command` running a Starlark handler; args (word/string/int/float/bool/player), `ctx.source`
  (player or console) + `ctx.args`. Re-registers on `/minelark reload`. Tier-1 tested + validated live.
- ✅ Text hover/click and translation **verified live in a real client** (2026-08-17): styled text
  renders, hovers show tooltips, and click actions run (note: vanilla blocks `run_command` for
  signed-message commands like `/say` from click events - use a non-chat command or `click_suggest`).

### M6 - Client scripts *(KubeJS: client)*
- ✅ **Client scripts get `events` + `text`/`translate`**: `runClient` now builds the same
  namespaced-event API as the server phase and returns a `ClientResult`; `MinelarkClient` is the
  client-side adapter (mirror of `Minelark`), firing Fabric client events into the callbacks. The
  `events` namespace is **phase-scoped**: referencing a server event from a client script (or the
  reverse) errors on the spot rather than registering a handler that could never fire.
- ✅ **Client lifecycle + tick**: `CLIENT_STARTED`, `CLIENT_STOPPING`, `CLIENT_TICK` (tick gated on
  `hasListeners`, like `SERVER_TICK`).
- ✅ **`client` + `debug` namespaces**: `client.player` / `client.world` (live local player/world),
  `client.send_chat` (chat or `/command`), `client.show_message` (local-only); `debug.set/remove/
  clear` add keyed lines to the F3 overlay via `DebugHudMixin` (client-only mixin config). Backed by
  a MC-agnostic `ClientAccess` interface (mirror of `PlayerActions`).
- ✅ **Tooltip event**: `ITEM_TOOLTIP` with `ctx.item` and editable `ctx.lines` seeded with the
  current lines (top-level style preserved) - reassign to add / remove / reorder; untouched = vanilla
  tooltip left as-is.
- ✅ **Client chat + editing**: `CLIENT_CHAT_RECEIVED` (incoming, cancel = hide; system messages
  rewritable via `ctx.message`, signed player chat cancel-only) and `CLIENT_CHAT_SENT` (outgoing,
  cancel = stop, rewrite via `ctx.message`). Editing uses the Fabric `MODIFY_*` events coordinated
  with the `ALLOW_*` pass via a thread-local so callbacks fire once.
- ✅ Tier-1 tested (`ClientScriptTest`, with a `FakeClient` for `ClientAccess`); MC-touching wiring is
  compile-validated + loads-clean only (the headless dev env has no client to smoke-test against).
- ✅ **HUD rendering** beyond the F3 overlay: the `hud` namespace (client scripts) - `hud.text(key,
  content, x=, y=, anchor=, color=, shadow=)` draws keyed, always-on text onto the screen (five
  anchors: the four corners + center), `hud.remove`/`hud.clear`. Content is a string or a `text(...)`
  component. Drawn via `HudRenderCallback`; keyed so a `CLIENT_TICK` callback can keep it live.
  MC-agnostic collection is Tier-1 tested (`HudApiTest`); **verified live in a real client**
  (2026-08-17: all five anchors render on screen). Docs auto-generated (`docs/api/hud.md`).
- ✅ **Networking** (server <-> client): the `net` namespace in both phases, riding one `CustomPayload`
  (`ScriptPayload`, a channel name + a JSON string, registered on the play S2C/C2S channels). Server:
  `net.send(player, channel, data)` / `net.broadcast(channel, data)` / `net.on(channel, handler)`;
  client: `net.send(channel, data)` / `net.on(channel, handler)`. `data` is any JSON-able value; a
  handler `ctx` carries `ctx.channel` / `ctx.data` (+ `ctx.player` on the server). This is the path
  that carries a server's own "events" out to clients. Handlers are swapped on `/minelark reload` and
  fired on the game thread. Sending the instant a player joins is handled: a send whose client channel
  isn't ready yet (tracked via `S2CPlayChannelEvents.REGISTER`, since `canSend` reports ready too
  early) is queued and flushed once the client registers the channel. Channel dispatch + sends are
  Tier-1 tested (`NetworkTest`, fake senders); **verified live in a real client** (2026-08-17: both
  directions + send-on-join round-tripped). Docs: hand-written `docs/api/net.md`.

### M7 - Interop & integrations
- ✅ **Curated interop bridge (sandbox-preserving), mod-compat helpers**: `mods` +
  `registry` namespaces in server + client scripts. `mods.loaded/version/name/list` query the
  Fabric mod list so a pack can branch on what else is installed; `registry.item_exists/
  block_exists/entity_exists/fluid_exists` and `registry.items/blocks/entities/fluids(namespace=)`
  read the game registries (bare ids default to `minecraft:`). Read-only discovery, no reflection -
  the curated stand-in for KubeJS's `Java.loadClass`. Backed by MC-agnostic `PlatformInfo` /
  `RegistryAccess` interfaces (mirrors of `ClientAccess`); the adapter uses `FabricLoader` +
  `Registries`. Tier-1 tested (`InteropTest`) and validated live on a headless server (registries
  populated, mods listed). Docs auto-generated (`docs/api/{mods,registry}.md`).
- ✅ **Content-type extension API**: mod-added types are handled two ways, both staying within the
  curated design. Registry-backed types resolve by `namespace:id` automatically (armor materials, plus
  the existing item/block/fluid/tag id passthrough). Non-registry types (sound groups, tool tiers,
  block shapes) get a public `MinelarkTypes` API + a `minelark:types` Fabric entrypoint
  (`MinelarkTypesInitializer`) so addon mods register more names; the valid-name set is threaded into
  the script layer's fail-fast validation via a `TypeCatalog`. Rarity stays fixed (vanilla enum). See
  `docs/extending.md`.
- ⏳ JEI/REI hooks (need third-party soft deps + can't be Tier-1 tested; deferred).

### M8 - Multiloader *(future)*
- Extract a `Platform` service interface (registration, events, paths).
- Fabric + NeoForge; `script` core stays shared and unchanged.
- CI matrix across loaders.

## Testing strategy (three tiers)

- **Tier 1 - interop unit tests (no MC).** Real scripts through the real interpreter; assert
  the collected declarations / dispatched callbacks. Fast; run on every CI build.
- **Tier 2 - pure-logic tests (no MC).** Recipe-JSON building, ingredient matching, tag math -
  all MC-agnostic data, so directly unit-testable.
- **Tier 3 - headless server / Fabric gametest.** In-world assertions; heavier, run per-release
  or on a PR label.

Goal: **every behaviour has a Tier-1 or Tier-2 test**, so KubeJS parity is provable without
launching Minecraft.

## CI/CD

- **CI** (`.github/workflows/build.yml`): JDK 21, `./gradlew build` (compile + all tests) on
  push/PR, with Gradle caching.
- **Release** (`.github/workflows/release.yml`): on `v*` tag, build with the tag version and
  attach the jar to a GitHub Release.
