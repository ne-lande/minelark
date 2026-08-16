package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The {@code commands} namespace for server scripts: register your own {@code /commands} that run a
 * Starlark handler.
 *
 * <pre>{@code
 * def greet(ctx):
 *     ctx.source.tell("Hello, " + ctx.args["who"] + "!")
 *
 * commands.register("greet", greet, args = [{"name": "who", "type": "word"}])
 * }</pre>
 *
 * <p>Collected specs are turned into Brigadier commands by the game adapter; {@link #invoke} runs a
 * handler (the same pattern as {@link Events#fire}).
 */
public final class CommandsApi implements StarlarkValue {
    private static final Pattern LITERAL = Pattern.compile("[a-z0-9_]+");

    private final List<CommandSpec> commands = new ArrayList<>();
    private final Log log;

    public CommandsApi(Log log) {
        this.log = log;
    }

    @StarlarkMethod(
            name = "register",
            doc = "Registers a `/command` that runs `handler(ctx)`. `ctx.source` is who ran it and "
                    + "`ctx.args` holds the parsed arguments.",
            parameters = {
                    @Param(name = "name", doc = "The command, e.g. `\"greet\"` or `\"warp home\"` (space-separated literals)."),
                    @Param(name = "handler", doc = "The `def handler(ctx): ...` to run."),
                    @Param(
                            name = "permission",
                            named = true,
                            defaultValue = "0",
                            doc = "The op level required (0 = everyone, up to 4)."),
                    @Param(
                            name = "args",
                            named = true,
                            defaultValue = "[]",
                            doc = "Argument specs, each `{\"name\": ..., \"type\": ...}` (or `[name, type]`). "
                                    + "Types: word, string, int, float, bool, player.")})
    public void register(String name, Object handler, StarlarkInt permission, Sequence<?> args)
            throws EvalException {
        if (!(handler instanceof StarlarkCallable fn)) {
            throw new EvalException("commands.register() handler must be a function");
        }
        List<String> literals = new ArrayList<>();
        for (String part : name.trim().split("\\s+")) {
            if (!LITERAL.matcher(part).matches()) {
                throw new EvalException("commands.register() invalid command name '" + name
                        + "' (use lowercase words: letters, digits, underscore)");
            }
            literals.add(part);
        }
        if (literals.isEmpty()) {
            throw new EvalException("commands.register() command name is empty");
        }
        int perm = permission.toIntUnchecked();
        if (perm < 0 || perm > 4) {
            throw new EvalException("commands.register() permission must be 0-4, got " + perm);
        }
        List<ArgSpec> argSpecs = new ArrayList<>();
        for (Object arg : args) {
            argSpecs.add(parseArg(arg));
        }
        commands.add(new CommandSpec(literals, perm, argSpecs, fn));
    }

    private ArgSpec parseArg(Object arg) throws EvalException {
        String argName;
        String argType;
        if (arg instanceof Dict<?, ?> dict) {
            argName = asString(dict.get("name"));
            argType = asString(dict.get("type"));
        } else if (arg instanceof Sequence<?> seq && seq.size() == 2) {
            argName = asString(seq.get(0));
            argType = asString(seq.get(1));
        } else {
            throw new EvalException(
                    "commands.register() each arg must be {\"name\": ..., \"type\": ...} or [name, type]");
        }
        if (argName == null || argName.isEmpty() || argType == null) {
            throw new EvalException("commands.register() each arg needs a name and a type");
        }
        if (!ArgSpec.TYPES.contains(argType)) {
            throw new EvalException("commands.register() unknown arg type '" + argType + "' (one of "
                    + ArgSpec.TYPES + ")");
        }
        return new ArgSpec(argName, argType);
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    /** The commands registered so far (in declaration order). */
    public List<CommandSpec> commands() {
        return List.copyOf(commands);
    }

    /** Runs a command handler on a fresh {@link StarlarkThread}; returns whether it succeeded. */
    public boolean invoke(CommandSpec spec, CommandContext ctx, ScriptLog sink) {
        String source = "command:" + spec.name();
        log.setSource(source);
        try (Mutability mu = Mutability.create(source)) {
            StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
            thread.setPrintHandler((t, message) -> sink.info("[" + source + "] " + message));
            Starlark.call(thread, spec.handler(), List.of(ctx), Map.of());
            return true;
        } catch (EvalException e) {
            sink.error("[" + source + "] " + e.getMessageWithStack());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sink.error("[" + source + "] interrupted");
            return false;
        } finally {
            log.setSource("");
        }
    }
}
