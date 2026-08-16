# Contributing to Minelark

Thanks for your interest in improving Minelark! This guide covers how to build, test, and submit
changes.

## Development setup

Minelark targets **Fabric, Minecraft 1.21.1, Java 21**. A Nix dev shell provides the JDK, Gradle,
and the docs toolchain:

```sh
nix develop            # or: direnv allow, then it loads automatically
```

No Nix? Just install **JDK 21** (Temurin recommended); the project ships a Gradle wrapper.

## Build, test, run

```sh
./gradlew build        # compile, run all tests, check docs are current, build the jar
./gradlew runClient    # launch a dev client
./gradlew runServer    # launch a dev server (needs run/eula.txt = eula=true)
```

## Project layout

Minelark keeps the scripting engine **free of Minecraft types** so it can be unit-tested without
launching the game:

- `src/main/java/ru/nelande/minelark/script/` - the interpreter, phase APIs, and data records
  (`ItemSpec`, `RecipeSpec`, `Recipes`, `Events`, ...). **No Minecraft imports.** This is where most
  logic lives and where tests focus.
- `src/main/java/ru/nelande/minelark/pack/` - the generated data pack (tags, recipes, loot) written
  to disk and served to the game.
- `src/main/java/ru/nelande/minelark/mixin/` - the mixin that injects the generated pack into the
  server's datapack manager.
- `src/main/java/ru/nelande/minelark/` - the thin Minecraft adapter (`Minelark`, the client
  entrypoint) that turns the engine's output into real game content.
- `src/test/java/.../script/` - JUnit tests that run real scripts through the real interpreter.

## Tests

Every behaviour should be provable **without launching Minecraft**. When you add or change a script
API, add a test under `src/test/java/ru/nelande/minelark/script/` that runs a `.star` snippet and
asserts on the collected result (see `StarlarkHostTest` / `ServerScriptTest`). Run `./gradlew test`.

For changes that touch Minecraft directly, a manual `./gradlew runServer` / `runClient` smoke test
is expected - describe what you checked in the PR.

## Documentation

- The **API reference** under `docs/api/` is **generated from the `@StarlarkMethod` annotations** -
  never edit it by hand. After changing an API, run `./gradlew generateApiDocs` and commit the
  result. CI (`./gradlew build`) fails if it is stale.
- Guide pages (`docs/getting-started.md`, `docs/language.md`) are hand-written.
- Preview the site with `mkdocs serve` (mkdocs + Material are in the Nix dev shell).

## Submitting changes

1. Branch off `main`.
2. Keep changes focused; match the style of the surrounding code.
3. Make sure `./gradlew build` passes (tests + doc check).
4. Update docs where relevant (annotations for API changes; guide pages for behaviour).
5. Open a pull request describing **what** changed and **how you verified it**.

By contributing, you agree that your contributions are licensed under the project's
**LGPL-3.0-or-later** license (see [LICENSE](LICENSE)).

See [ROADMAP.md](ROADMAP.md) for what's planned and where help is most useful.
