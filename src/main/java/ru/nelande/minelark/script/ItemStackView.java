package ru.nelande.minelark.script;

import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * A read-only view of an item stack, e.g. {@code ctx.player.held_item}. MC-agnostic: the game
 * adapter fills it from an {@code ItemStack}.
 */
public final class ItemStackView implements StarlarkValue {
    private final String id;
    private final int count;
    private final String name;
    private final boolean empty;

    public ItemStackView(String id, int count, String name, boolean empty) {
        this.id = id;
        this.count = count;
        this.name = name;
        this.empty = empty;
    }

    /** The canonical empty stack (nothing in hand). */
    public static ItemStackView empty() {
        return new ItemStackView("minecraft:air", 0, "Air", true);
    }

    @StarlarkMethod(name = "id", structField = true, doc = "The item id, e.g. `minecraft:diamond`.")
    public String id() {
        return id;
    }

    @StarlarkMethod(name = "count", structField = true, doc = "How many items are in the stack.")
    public int count() {
        return count;
    }

    @StarlarkMethod(name = "name", structField = true, doc = "The stack's display name.")
    public String name() {
        return name;
    }

    @StarlarkMethod(name = "is_empty", structField = true, doc = "Whether the stack is empty (nothing there).")
    public boolean isEmpty() {
        return empty;
    }

    @Override
    public String toString() {
        return count + "x " + id;
    }
}
