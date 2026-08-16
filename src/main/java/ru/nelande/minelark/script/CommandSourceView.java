package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

import java.util.function.Consumer;

/**
 * Who ran a custom command: {@code ctx.source}. Might be a player or the console. MC-agnostic; the
 * adapter builds it from the Brigadier {@code ServerCommandSource}.
 */
public final class CommandSourceView implements StarlarkValue {
    private final String name;
    private final PlayerView player;      // null when run from the console / a command block
    private final LevelView level;
    private final Consumer<MineText> feedback;

    public CommandSourceView(String name, PlayerView player, LevelView level, Consumer<MineText> feedback) {
        this.name = name;
        this.player = player;
        this.level = level;
        this.feedback = feedback;
    }

    @StarlarkMethod(name = "name", structField = true, doc = "The name of whoever ran the command.")
    public String name() {
        return name;
    }

    @StarlarkMethod(name = "is_player", structField = true, doc = "Whether a player (not the console) ran it.")
    public boolean isPlayer() {
        return player != null;
    }

    @StarlarkMethod(
            name = "player",
            structField = true,
            doc = "The player who ran the command, or `None` if the console did. Check `is_player` first.")
    public Object player() {
        return player != null ? player : Starlark.NONE;
    }

    @StarlarkMethod(
            name = "level",
            structField = true,
            doc = "The world the command ran in, or `None` (e.g. the console has no world).")
    public Object level() {
        return level != null ? level : Starlark.NONE;
    }

    @StarlarkMethod(
            name = "tell",
            doc = "Sends command feedback to the source. Accepts a string or a `text(...)` component.",
            parameters = {@Param(name = "message", doc = "The string or component to send.")})
    public void tell(Object message) {
        feedback.accept(MineText.coerce(message));
    }

    @Override
    public String toString() {
        return name;
    }
}
