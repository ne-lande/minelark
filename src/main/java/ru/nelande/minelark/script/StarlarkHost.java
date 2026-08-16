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
        StartupApi api = new StartupApi();
        Log console = new Log(log);
        ImmutableMap<String, Object> env = environment(console, api);
        new ScriptEngine(startupDir, env, console, log).runAll();
        return new StartupResult(api.items(), api.blocks());
    }

    /**
     * Runs the server phase (data generation + {@code /minelark reload}) and returns the reloadable
     * content it declared (recipes, and later loot).
     */
    public static ServerResult runServer(Path serverDir, ScriptLog log) {
        Log console = new Log(log);
        Recipes recipes = new Recipes();
        Events events = new Events(console, Events.Scope.SERVER);
        CommandsApi commands = new CommandsApi(console);
        ImmutableMap<String, Object> env = environmentWith(
                console,
                Map.of("recipes", recipes, "events", events, "commands", commands),
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
        Log console = new Log(log);
        Events events = new Events(console, Events.Scope.CLIENT);
        DebugApi debug = new DebugApi();
        ImmutableMap<String, Object> env = environmentWith(
                console,
                Map.of("events", events, "client", new ClientApi(access), "debug", debug),
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
