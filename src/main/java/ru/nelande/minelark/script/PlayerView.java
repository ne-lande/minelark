package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

import java.util.function.Consumer;

/**
 * A view of a player, handed to event callbacks as {@code ctx.player}. Read its identity, position,
 * health, held item, and world; and drive a few safe actions ({@code tell}, {@code give},
 * {@code teleport}).
 *
 * <p>Kept free of Minecraft types so it is unit-testable: the game adapter builds it with the
 * player's current data and a {@link PlayerActions} that bridges the actions to the real player.
 */
public final class PlayerView implements StarlarkValue {
    private final String name;
    private final String uuid;
    private final double x;
    private final double y;
    private final double z;
    private final double health;
    private final ItemStackView heldItem;
    private final LevelView level;
    private final PlayerActions actions;

    public PlayerView(String name, String uuid, double x, double y, double z, double health,
                      ItemStackView heldItem, LevelView level, PlayerActions actions) {
        this.name = name;
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.health = health;
        this.heldItem = heldItem;
        this.level = level;
        this.actions = actions;
    }

    /** Convenience for tests and message-only uses: identity plus a {@code tell} sink, sane defaults. */
    public PlayerView(String name, String uuid, Consumer<MineText> tell) {
        this(name, uuid, 0, 0, 0, 20, ItemStackView.empty(),
                new LevelView("minecraft:overworld", 0, true, false),
                new PlayerActions() {
                    @Override
                    public void tell(MineText message) {
                        tell.accept(message);
                    }

                    @Override
                    public void give(String itemId, int count) {
                    }

                    @Override
                    public void teleport(double tx, double ty, double tz) {
                    }
                });
    }

    // --- data ---

    @StarlarkMethod(name = "name", structField = true, doc = "The player's display name.")
    public String name() {
        return name;
    }

    @StarlarkMethod(name = "uuid", structField = true, doc = "The player's UUID as a string.")
    public String uuid() {
        return uuid;
    }

    @StarlarkMethod(name = "x", structField = true, doc = "The player's x position.")
    public double x() {
        return x;
    }

    @StarlarkMethod(name = "y", structField = true, doc = "The player's y position.")
    public double y() {
        return y;
    }

    @StarlarkMethod(name = "z", structField = true, doc = "The player's z position.")
    public double z() {
        return z;
    }

    @StarlarkMethod(name = "health", structField = true, doc = "The player's current health (2 per heart).")
    public double health() {
        return health;
    }

    @StarlarkMethod(name = "held_item", structField = true, doc = "The item in the player's main hand.")
    public ItemStackView heldItem() {
        return heldItem;
    }

    @StarlarkMethod(name = "level", structField = true, doc = "The world the player is in.")
    public LevelView level() {
        return level;
    }

    // --- actions ---

    @StarlarkMethod(
            name = "tell",
            doc = "Sends a system message to this player. Accepts a plain string or a `text(...)` component.",
            parameters = {@Param(name = "message", doc = "The string or `text(...)` component to send.")})
    public void tell(Object message) {
        actions.tell(MineText.coerce(message));
    }

    @StarlarkMethod(
            name = "give",
            doc = "Gives the player an item. Unknown ids are ignored.",
            parameters = {
                    @Param(name = "item", doc = "The item id, e.g. `\"minecraft:diamond\"` or a handle."),
                    @Param(name = "count", named = true, defaultValue = "1", doc = "How many to give.")})
    public void give(Object item, StarlarkInt count) {
        actions.give(String.valueOf(item), count.toIntUnchecked());
    }

    @StarlarkMethod(
            name = "teleport",
            doc = "Teleports the player to the given coordinates within their current world.",
            parameters = {
                    @Param(name = "x", doc = "Target x."),
                    @Param(name = "y", doc = "Target y."),
                    @Param(name = "z", doc = "Target z.")})
    public void teleport(Object x, Object y, Object z) throws EvalException {
        actions.teleport(toDouble(x), toDouble(y), toDouble(z));
    }

    private static double toDouble(Object value) throws EvalException {
        if (value instanceof StarlarkInt i) {
            return i.toIntUnchecked();
        }
        if (value instanceof StarlarkFloat f) {
            return f.toDouble();
        }
        throw new EvalException("expected a number, got " + value);
    }

    @Override
    public String toString() {
        return name;
    }
}
