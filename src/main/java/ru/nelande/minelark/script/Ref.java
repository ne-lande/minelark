package ru.nelande.minelark.script;

import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkValue;

/**
 * A handle to a registered thing (currently an item or block), returned by {@code item(...)} /
 * {@code block(...)}. It carries the full {@code namespace:path} id and can be used anywhere an id
 * string is accepted (recipes, {@code drops}, ...), so scripts don't have to repeat id strings:
 *
 * <pre>{@code
 * ruby = item("ruby", rarity = "rare")
 * block("ruby_ore", drops = ruby)
 * }</pre>
 *
 * <p>Printing or {@code str()}-ing a handle yields its id.
 */
public final class Ref implements StarlarkValue {
    private final String id;

    Ref(String id) {
        this.id = id;
    }

    /** The full {@code namespace:path} id. */
    public String id() {
        return id;
    }

    @Override
    public void repr(Printer printer) {
        printer.append(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
