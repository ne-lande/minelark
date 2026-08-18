package ru.nelande.minelark.script;

import com.google.common.collect.ImmutableMap;
import net.starlark.java.eval.Starlark;

import java.nio.file.Path;
import java.util.Map;

/**
 * Entry point for running scripts of a given lifecycle phase. Builds the phase's predeclared
 * globals (the {@code console} namespace plus the phase's builtins) and drives the
 * {@link ScriptEngine}.
 */
public final class StarlarkHost {
    private StarlarkHost() {
    }

    /**
     * Runs the startup phase: evaluates every {@code .star} file in {@code startupDir} and returns
     * the content they declared. Missing directories yield an empty result.
     */
    public static StartupResult runStartup(Path startupDir, ScriptLog log) {
        return runStartup(startupDir, TypeCatalog.VANILLA_DEFAULTS, log);
    }

    /**
     * Like {@link #runStartup(Path, ScriptLog)}, but with a live {@link TypeCatalog} of valid
     * sound/tool-tier/shape/armor-material names (built-in defaults plus addon-registered types).
     */
    public static StartupResult runStartup(Path startupDir, TypeCatalog catalog, ScriptLog log) {
        StartupApi api = new StartupApi(catalog);
        Log console = new Log(log);
        ImmutableMap<String, Object> env = environment(console, api);
        new ScriptEngine(startupDir, env, console, log).runAll();
        return new StartupResult(api.items(), api.blocks(), api.fluids());
    }

    /** Like the full {@code runServer} with no interop bridge and an in-memory (non-persistent) store. */
    public static ServerResult runServer(Path serverDir, ScriptLog log) {
        return runServer(serverDir, PlatformInfo.EMPTY, RegistryAccess.EMPTY, new Storage(null), log);
    }

    /** Like the full {@code runServer} with in-memory (non-persistent) stores and no live network. */
    public static ServerResult runServer(
            Path serverDir, PlatformInfo platform, RegistryAccess registry, ScriptLog log) {
        return runServer(serverDir, platform, registry, new Storage(null), new Storage(null),
                ServerNetwork.NOOP, log);
    }

    /** Like the full {@code runServer} with an in-memory (non-persistent) per-world store, no network. */
    public static ServerResult runServer(
            Path serverDir, PlatformInfo platform, RegistryAccess registry, Storage storage, ScriptLog log) {
        return runServer(serverDir, platform, registry, storage, new Storage(null), ServerNetwork.NOOP, log);
    }

    /** Like the full {@code runServer} without a live network (a no-op {@code net} sender). */
    public static ServerResult runServer(
            Path serverDir, PlatformInfo platform, RegistryAccess registry,
            Storage storage, Storage world, ScriptLog log) {
        return runServer(serverDir, platform, registry, storage, world, ServerNetwork.NOOP, log);
    }

    /**
     * Runs the server phase (data generation + {@code /minelark reload}) and returns the reloadable
     * content it declared (recipes, tags, loot, ...). The {@code platform}/{@code registry} bridge
     * backs the {@code mods}/{@code registry} namespaces; {@code storage} is the install-global
     * persistent store (and hands out per-player stores via {@code storage.player(...)}), {@code world}
     * is the per-world store; {@code network} backs the {@code net} namespace.
     */
    public static ServerResult runServer(
            Path serverDir, PlatformInfo platform, RegistryAccess registry,
            Storage storage, Storage world, ServerNetwork network, ScriptLog log) {
        Log console = new Log(log);
        Recipes recipes = new Recipes();
        Tags tags = new Tags();
        Loot loot = new Loot();
        Datapack datapack = new Datapack();
        Events events = new Events(console, Events.Scope.SERVER);
        CommandsApi commands = new CommandsApi(console);
        ServerNetworkApi net = new ServerNetworkApi(network, console);
        ImmutableMap<String, Object> env = environmentWith(
                console,
                Map.ofEntries(
                        Map.entry("recipes", recipes),
                        Map.entry("tags", tags),
                        Map.entry("loot", loot),
                        Map.entry("datapack", datapack),
                        Map.entry("storage", storage),
                        Map.entry("world", world),
                        Map.entry("net", net),
                        Map.entry("events", events),
                        Map.entry("commands", commands),
                        Map.entry("mods", new ModsApi(platform)),
                        Map.entry("registry", new RegistryApi(registry))),
                new TextApi());
        int scripts = new ScriptEngine(serverDir, env, console, log).runAll();
        return new ServerResult(recipes.recipes(), recipes.removals(), tags.tags(), loot.entityDropSpecs(),
                loot.injectSpecs(), datapack.jsonFiles(), events, commands, net, scripts);
    }

    /**
     * Runs the client phase (on client startup) and returns what it registered: the event callbacks
     * (client lifecycle, tick, tooltip, chat) and the {@code debug} overlay lines. Client scripts get
     * the {@code events} namespace, the {@code text}/{@code translate} builtins, the {@code client}
     * namespace (local player/world/actions, backed by {@code access}), and the {@code debug} namespace.
     */
    public static ClientResult runClient(Path clientDir, ClientAccess access, ScriptLog log) {
        return runClient(clientDir, access, PlatformInfo.EMPTY, RegistryAccess.EMPTY, ClientNetwork.NOOP, log);
    }

    /** Like {@link #runClient(Path, ClientAccess, ScriptLog)}, plus the {@code mods}/{@code registry} bridge. */
    public static ClientResult runClient(
            Path clientDir, ClientAccess access, PlatformInfo platform, RegistryAccess registry, ScriptLog log) {
        return runClient(clientDir, access, platform, registry, ClientNetwork.NOOP, log);
    }

    /** Like the full {@code runClient}, plus a {@link ClientNetwork} backing the {@code net} namespace. */
    public static ClientResult runClient(
            Path clientDir, ClientAccess access, PlatformInfo platform, RegistryAccess registry,
            ClientNetwork network, ScriptLog log) {
        Log console = new Log(log);
        Events events = new Events(console, Events.Scope.CLIENT);
        DebugApi debug = new DebugApi();
        HudApi hud = new HudApi();
        ClientNetworkApi net = new ClientNetworkApi(network, console);
        ImmutableMap<String, Object> env = environmentWith(
                console,
                Map.of(
                        "events", events,
                        "client", new ClientApi(access),
                        "debug", debug,
                        "hud", hud,
                        "net", net,
                        "mods", new ModsApi(platform),
                        "registry", new RegistryApi(registry)),
                new TextApi());
        int scripts = new ScriptEngine(clientDir, env, console, log).runAll();
        return new ClientResult(events, debug, hud, net, scripts);
    }

    /**
     * Builds a live REPL session for the in-game console ({@code /minelark eval}). It sees the
     * server's read/inspect surface - {@code log}, {@code storage}, {@code world}, {@code mods},
     * {@code registry}, and the {@code text}/{@code translate} builtins - plus {@code print}. Content
     * registration ({@code recipes}, {@code events}, ...) is deliberately left out: those only take
     * effect through a real script + {@code /minelark reload}, so exposing them in a REPL would just
     * mislead.
     */
    public static ConsoleSession newServerConsole(
            PlatformInfo platform, RegistryAccess registry, Storage storage, Storage world, ScriptLog log) {
        Log console = new Log(log);
        ImmutableMap<String, Object> env = environmentWith(
                console,
                Map.of(
                        "storage", storage,
                        "world", world,
                        "mods", new ModsApi(platform),
                        "registry", new RegistryApi(registry)),
                new TextApi());
        return new ConsoleSession(env);
    }

    /**
     * Builds the predeclared globals for a phase: the standard Starlark universe, the {@code console}
     * namespace, and the top-level builtins contributed by each API holder.
     */
    static ImmutableMap<String, Object> environment(Log console, Object... builtinHolders) {
        return environmentWith(console, Map.of(), builtinHolders);
    }

    /** Like {@link #environment}, plus extra named namespace values (e.g. {@code recipes}). */
    static ImmutableMap<String, Object> environmentWith(
            Log console, Map<String, Object> namespaces, Object... builtinHolders) {
        ImmutableMap.Builder<String, Object> env = ImmutableMap.builder();
        env.putAll(Starlark.UNIVERSE);
        env.put("log", console);
        Starlark.addMethods(env, new PreludeApi());   // require/clamp/lerp/rgb, in every phase
        namespaces.forEach(env::put);
        for (Object holder : builtinHolders) {
            Starlark.addMethods(env, holder);
        }
        return env.buildOrThrow();
    }
}
