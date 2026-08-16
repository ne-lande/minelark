package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Contributes the top-level {@code text(...)} and {@code translate(...)} builtins that build
 * {@link MineText} components. Available anywhere messages are produced (server scripts, for event
 * callbacks). Stateless - just a factory the host installs into the environment.
 */
public final class TextApi {

    @StarlarkMethod(
            name = "text",
            doc = "Builds a text component from a string. Chain `.color(...)`, `.bold()`, `.hover(...)`, "
                    + "`.click_run(...)`, `.append(...)` and so on to style it.",
            parameters = {@Param(name = "content", doc = "The literal text.")})
    public MineText text(String content) {
        return MineText.literal(content);
    }

    @StarlarkMethod(
            name = "translate",
            doc = "Builds a component from a translation key, resolved on each player's client. Optional "
                    + "arguments fill the key's `%s` placeholders.",
            parameters = {
                    @Param(name = "key", doc = "The translation key, e.g. `\"block.minecraft.stone\"`."),
                    @Param(
                            name = "args",
                            named = true,
                            defaultValue = "[]",
                            doc = "Placeholder values (strings or `text(...)` components).")})
    public MineText translate(String key, Sequence<?> args) {
        List<MineText> parts = new ArrayList<>();
        for (Object arg : args) {
            parts.add(MineText.coerce(arg));
        }
        return MineText.translate(key, parts);
    }
}
