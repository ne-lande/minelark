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

    /** Like {@link #runServer(Path, PlatformInfo, RegistryAccess, ScriptLog)} with no interop bridge. */
    public static ServerResult runServer(Path serverDir, ScriptLog log) {
        return runServer(serverDir, PlatformInfo.EMPTY, RegistryAccess.EMPTY, log);
    }

    /**
     * Runs the server phase (data generation + {@code /minelark reload}) and returns the reloadable
     * content it declared (recipes, and later loot). The {@code platform}/{@code registry} bridge
     * backs the {@code mods} and {@code registry} namespaces.
     */
    public static ServerResult runServer(
            Path serverDir, PlatformInfo platform, RegistryAccess registry, ScriptLog log) {
        Log console = new Log(log);
        Recipes recipes = new Recipes();
        Events events = new Events(console, Events.Scope.SERVER);
        CommandsApi commands = new CommandsApi(console);
        ImmutableMap<String, Object> env = environmentWith(
                console,
                Map.of(
                        "recipes", recipes,
                        "events", events,
                        "commands", commands,
                        "mods", new ModsApi(platform),
                        "registry", new RegistryApi(registry)),
                new TextApi());
        int scripts = new ScriptEngine(serverDir, env, console, log).runAll();
        return new ServerResult(recipes.recipes(), events, commands, scripts);
    }

    /**
     * Runs the client phase (on client startup) and returns what it registered: the event callbacks
     * (client lifecycle, tick, tooltip, chat) and the {@code debug} overlay lines. Client scripts get
     * the {@code events} namespace, the {@code text}/{@code translate} builtins, the {@code client}
     * namespace (local player/world/actions, backed by {@code access}), and the {@code debug} namespace.
     */
    public static ClientResult runClient(Path clientDir, ClientAccess access, ScriptLog log) {
        return runClient(clientDir, access, PlatformInfo.EMPTY, RegistryAccess.EMPTY, log);
    }

    /** Like {@link #runClient(Path, ClientAccess, ScriptLog)}, plus the {@code mods}/{@code registry} bridge. */
    public static ClientResult runClient(
            Path clientDir, ClientAccess access, PlatformInfo platform, RegistryAccess registry, ScriptLog log) {
        Log console = new Log(log);
        Events events = new Events(console, Events.Scope.CLIENT);
        DebugApi debug = new DebugApi();
        ImmutableMap<String, Object> env = environmentWith(
                console,
                Map.of(
                        "events", events,
                        "client", new ClientApi(access),
                        "debug", debug,
                        "mods", new ModsApi(platform),
                        "registry", new RegistryApi(registry)),
                new TextApi());
        int scripts = new ScriptEngine(clientDir, env, console, log).runAll();
        return new ClientResult(events, debug, scripts);
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
        namespaces.forEach(env::put);
        for (Object holder : builtinHolders) {
            Starlark.addMethods(env, holder);
        }
        return env.buildOrThrow();
    }
}
