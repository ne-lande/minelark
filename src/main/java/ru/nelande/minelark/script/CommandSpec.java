package ru.nelande.minelark.script;

import net.starlark.java.eval.StarlarkCallable;

import java.util.List;

/**
 * A command a server script registered: the literal path (e.g. {@code ["warp", "home"]}), the op
 * permission level required, its arguments, and the Starlark handler. MC-agnostic - the game adapter
 * turns this into a Brigadier command node.
 */
public record CommandSpec(List<String> literals, int permission, List<ArgSpec> args, StarlarkCallable handler) {

    public CommandSpec {
        literals = List.copyOf(literals);
        args = List.copyOf(args);
    }

    /** The command as typed, e.g. {@code warp home}. */
    public String name() {
        return String.join(" ", literals);
    }
}
