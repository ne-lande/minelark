package ru.nelande.minelark.script;

import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.StarlarkValue;

/**
 * The {@code ctx} passed to a custom command's handler: who ran it ({@code ctx.source}) and the
 * parsed arguments ({@code ctx.args}, a dict keyed by argument name).
 */
public final class CommandContext implements StarlarkValue {
    private final CommandSourceView source;
    private final Dict<String, Object> args;

    public CommandContext(CommandSourceView source, Dict<String, Object> args) {
        this.source = source;
        this.args = args;
    }

    @StarlarkMethod(name = "source", structField = true, doc = "Who ran the command (a player or the console).")
    public CommandSourceView source() {
        return source;
    }

    @StarlarkMethod(name = "args", structField = true, doc = "The parsed arguments, keyed by name.")
    public Dict<String, Object> args() {
        return args;
    }
}
