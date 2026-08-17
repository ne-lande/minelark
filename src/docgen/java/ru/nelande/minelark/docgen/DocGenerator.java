package ru.nelande.minelark.docgen;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkInt;
import ru.nelande.minelark.script.Datapack;
import ru.nelande.minelark.script.HudApi;
import ru.nelande.minelark.script.Log;
import ru.nelande.minelark.script.Loot;
import ru.nelande.minelark.script.ModsApi;
import ru.nelande.minelark.script.Recipes;
import ru.nelande.minelark.script.RegistryApi;
import ru.nelande.minelark.script.StartupApi;
import ru.nelande.minelark.script.Storage;
import ru.nelande.minelark.script.Tags;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Generates the per-phase API reference under {@code docs/api/} directly from the
 * {@link StarlarkMethod} / {@link Param} annotations on the scripting API classes, so the docs are
 * always in lock-step with the code (single source of truth).
 *
 * <p>Usage: {@code DocGenerator <projectDir> [--check]}. With {@code --check} it writes nothing and
 * exits non-zero if any file is missing or out of date (used by the {@code checkApiDocs} task/CI).
 */
public final class DocGenerator {

    /** A documented scripting phase: page title, output file name, API class, and intro text. */
    private record Phase(String title, String fileName, Class<?> api, String namespace, String intro) {
    }

    private static final List<Phase> PHASES = List.of(
            new Phase(
                    "Common API Reference",
                    "common.md",
                    Log.class,
                    "log",
                    """
                    The `log` namespace is available to scripts in **every** phase. Use it to log
                    diagnostic messages; they appear in the game log / console tagged with the script
                    name and level.
                    """),
            new Phase(
                    "Startup API Reference",
                    "startup.md",
                    StartupApi.class,
                    "",
                    """
                    Functions available to scripts in the **`minelark/startup/`** folder. Startup \
                    scripts run once at launch, before the game freezes its registries, which is \
                    why this is where content is registered.

                    All content is registered under the **`minelark:`** namespace.
                    """),
            new Phase(
                    "Server API Reference",
                    "server.md",
                    Recipes.class,
                    "recipes",
                    """
                    The **`recipes`** namespace, available to scripts in the **`minelark/server/`** \
                    folder. Recipes are reloadable data - re-apply them in-game with `/minelark \
                    reload`. Ids and tags without a namespace default to `minecraft:`; prefix a tag \
                    with `#`.

                    Server scripts also react to [events](events.md), send styled [text](text.md), \
                    and register their own [commands](commands.md).
                    """),
            new Phase(
                    "Mods API Reference",
                    "mods.md",
                    ModsApi.class,
                    "mods",
                    """
                    The **`mods`** namespace, available to **server** and **client** scripts. Use it to \
                    check what else is installed so a pack can adapt to its environment - for example, \
                    only add a recipe when the mod it depends on is present:

                    ```python
                    if mods.loaded("create"):
                        recipes.shapeless("create:cogwheel", ["minelark:ruby"])
                    ```
                    """),
            new Phase(
                    "Registry API Reference",
                    "registry.md",
                    RegistryApi.class,
                    "registry",
                    """
                    The **`registry`** namespace, available to **server** and **client** scripts. Query \
                    the game registries to see whether an item/block/entity/fluid exists and to list the \
                    ids other mods registered. Ids without a namespace default to `minecraft:`.

                    This is read-only discovery - a sandbox-preserving window onto the game's content, \
                    with no reflection into other mods.
                    """),
            new Phase(
                    "Tags API Reference",
                    "tags.md",
                    Tags.class,
                    "tags",
                    """
                    The **`tags`** namespace, available to scripts in the **`minelark/server/`** folder.
                    Add any ids - vanilla, Minelark's, or another mod's - to item / block / fluid /
                    entity tags. Written into the generated data pack, reloadable with `/minelark
                    reload`. A bare tag name uses the conventional `c:` namespace; a bare member id
                    defaults to `minecraft:`; a `#`-prefixed member includes another tag.
                    """),
            new Phase(
                    "Loot API Reference",
                    "loot.md",
                    Loot.class,
                    "loot",
                    """
                    The **`loot`** namespace, available to scripts in the **`minelark/server/`** folder.
                    Replace what an entity drops, or inject extra drops into an existing loot table.
                    Each drop is an item id, or a `{"item": id, "count": N or [min, max], "chance":
                    0.0-1.0}` dict. Reloadable with `/minelark reload`.
                    """),
            new Phase(
                    "Datapack API Reference",
                    "datapack.md",
                    Datapack.class,
                    "datapack",
                    """
                    The **`datapack`** namespace, available to scripts in the **`minelark/server/`**
                    folder. A generic escape hatch: write raw JSON files into Minelark's generated data
                    pack for anything not modelled directly (advancements, predicates, dimensions,
                    worldgen, ...). Reloadable with `/minelark reload`.
                    """),
            new Phase(
                    "Storage API Reference",
                    "storage.md",
                    Storage.class,
                    "storage",
                    """
                    Persistent key-value storage for scripts in the **`minelark/server/`** folder. A
                    store survives `/minelark reload` and server restarts - handy from
                    [event](events.md) callbacks (a join counter, a flag a command sets). Values are any
                    JSON-able value (dict, list, string, number, bool, `None`); each change is saved to
                    disk immediately.

                    There are three scopes. **`storage`** and **`world`** are namespaces you call
                    methods on directly; a per-player store is one you get from **`storage.player(uuid)`**.
                    All three have the same `set` / `get` / `has` / `delete` / `keys` / `clear` methods
                    (documented below with the `storage.` prefix - `world.set(...)` and
                    `storage.player(u).set(...)` work identically):

                    - **`storage`** - install-global, shared by every world (`<gamedir>/minelark/storage.json`).
                    - **`world`** - saved with the current world, so it is isolated between worlds.
                    - **`storage.player(uuid)`** - per-world **and** per-player (pass `ctx.player.uuid`).

                    ```python
                    def on_join(ctx):
                        # install-global: shared by every world
                        storage.set("total_joins", storage.get("total_joins", 0) + 1)

                        # per-world: saved with this world
                        world.set("last_player", ctx.player.name)

                        # per-world AND per-player
                        me = storage.player(ctx.player.uuid)
                        me.set("visits", me.get("visits", 0) + 1)

                    events.minelark.PLAYER_JOINED.on(on_join)
                    ```

                    `world` and `storage.player(...)` are only bound once a world has loaded, so use them
                    from an event or command callback rather than at the top level of a script.
                    """),
            new Phase(
                    "HUD API Reference",
                    "hud.md",
                    HudApi.class,
                    "hud",
                    """
                    The **`hud`** namespace, available to scripts in the **`minelark/client/`** folder.
                    Draw your own always-on text onto the game screen (the [debug](client.md) namespace,
                    by contrast, only shows on the F3 overlay). Each element is keyed, so calling
                    `hud.text(...)` again with the same key replaces it - set it from a
                    [`CLIENT_TICK`](events.md) callback to keep it live.

                    ```python
                    def on_tick(ctx):
                        p = client.player
                        if p != None:
                            hud.text("pos", text(str(int(p.x)) + " " + str(int(p.y)) + " " + str(int(p.z)))
                                     .color("aqua"), x = 4, y = 4, anchor = "top_left")

                    events.minelark.CLIENT_TICK.on(on_tick)
                    ```
                    """)
            // Note: the `events` API (docs/api/events.md) is hand-written - its nested
            // `events.<namespace>.<EVENT>` shape doesn't fit this flat generator.
    );

    private static final String GENERATED_NOTICE =
            "<!-- Generated by `./gradlew generateApiDocs` from @StarlarkMethod annotations. Do not edit by hand. -->";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: DocGenerator <projectDir> [--check]");
            System.exit(2);
            return;
        }
        Path projectDir = Path.of(args[0]);
        boolean check = args.length > 1 && "--check".equals(args[1]);
        Path apiDir = projectDir.resolve("docs").resolve("api");

        List<String> stale = new ArrayList<>();
        if (!check) {
            Files.createDirectories(apiDir);
        }

        for (Phase phase : PHASES) {
            String expected = render(phase);
            Path file = apiDir.resolve(phase.fileName());
            if (check) {
                String actual = Files.exists(file) ? Files.readString(file) : "";
                if (!expected.equals(actual)) {
                    stale.add("docs/api/" + phase.fileName());
                }
            } else {
                Files.writeString(file, expected);
                System.out.println("Generated docs/api/" + phase.fileName());
            }
        }

        if (check && !stale.isEmpty()) {
            System.err.println("API docs are out of date: " + String.join(", ", stale));
            System.err.println("Run `./gradlew generateApiDocs` and commit the result.");
            System.exit(1);
        }
    }

    private static String render(Phase phase) {
        StringBuilder sb = new StringBuilder();
        sb.append(GENERATED_NOTICE).append("\n\n");
        sb.append("# ").append(phase.title()).append("\n\n");
        sb.append(phase.intro().strip()).append("\n\n");

        List<Method> builtins = Arrays.stream(phase.api().getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(StarlarkMethod.class))
                .sorted(Comparator.comparing(m -> m.getAnnotation(StarlarkMethod.class).name()))
                .toList();

        for (Method m : builtins) {
            sb.append("---\n\n");
            renderMethod(sb, m, phase.namespace());
        }

        sb.append("---\n\n");
        sb.append("*The usual Starlark builtins (`print`, `len`, `range`, and so on) work here too. "
                + "See [The Starlark Language](../language.md).*\n");
        return sb.toString();
    }

    private static void renderMethod(StringBuilder sb, Method m, String namespace) {
        StarlarkMethod sm = m.getAnnotation(StarlarkMethod.class);
        Param[] params = sm.parameters();
        Class<?>[] types = m.getParameterTypes();

        // Signature, e.g.  item(id, max_stack_size = 64)  or  log.info(message)
        String qualifiedName = namespace.isEmpty() ? sm.name() : namespace + "." + sm.name();
        sb.append("## `").append(qualifiedName).append('(');
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params[i].name());
            if (!params[i].defaultValue().isEmpty()) {
                sb.append(" = ").append(params[i].defaultValue());
            }
        }
        sb.append(")`\n\n");

        if (!sm.doc().isEmpty()) {
            sb.append(sm.doc().strip()).append("\n\n");
        }

        if (params.length > 0) {
            sb.append("**Parameters**\n\n");
            sb.append("| Name | Type | Required | Default | Description |\n");
            sb.append("|---|---|---|---|---|\n");
            for (int i = 0; i < params.length; i++) {
                Param p = params[i];
                boolean required = p.defaultValue().isEmpty();
                String type = i < types.length ? starlarkType(types[i]) : "";
                String doc = p.doc().isEmpty() ? "" : p.doc().strip().replace("\n", " ");
                sb.append("| `").append(p.name()).append("` | ")
                        .append(type).append(" | ")
                        .append(required ? "yes" : "no").append(" | ")
                        .append(required ? "-" : "`" + p.defaultValue() + "`").append(" | ")
                        .append(doc).append(" |\n");
            }
            sb.append('\n');
        }
    }

    private static String starlarkType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == StarlarkInt.class || type == int.class || type == Integer.class) {
            return "int";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "bool";
        }
        if (type == Object.class) {
            // Params typed Object accept a few Starlark types (e.g. an int or float, or an id
            // string or handle); the per-parameter description says which.
            return "any";
        }
        return type.getSimpleName().toLowerCase();
    }

    private DocGenerator() {
    }
}
