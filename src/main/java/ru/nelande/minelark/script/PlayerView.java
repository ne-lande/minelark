package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
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
        actions.teleport(Nums.toDouble(x), Nums.toDouble(y), Nums.toDouble(z));
    }

    @StarlarkMethod(name = "heal", doc = "Restores the player to full health.")
    public void heal() {
        actions.heal();
    }

    @StarlarkMethod(
            name = "set_health",
            doc = "Sets the player's health (2 per heart), clamped to their maximum.",
            parameters = {@Param(name = "health", doc = "The new health value.")})
    public void setHealth(Object health) throws EvalException {
        actions.setHealth(Nums.toDouble(health));
    }

    @StarlarkMethod(
            name = "damage",
            doc = "Deals damage to the player (2 per heart).",
            parameters = {@Param(name = "amount", doc = "How much damage to deal.")})
    public void damage(Object amount) throws EvalException {
        actions.damage(Nums.toDouble(amount));
    }

    @StarlarkMethod(
            name = "effect",
            doc = "Applies a status effect. `amplifier` is the MC level (0 = level I, 1 = level II). "
                    + "Unknown effect ids are ignored.",
            parameters = {
                    @Param(name = "effect", doc = "The effect id, e.g. `\"speed\"` or `\"minecraft:regeneration\"`."),
                    @Param(name = "seconds", named = true, defaultValue = "30", doc = "Duration in seconds."),
                    @Param(name = "amplifier", named = true, defaultValue = "0", doc = "Level, 0-based (0 = I)."),
                    @Param(name = "show_particles", named = true, defaultValue = "True",
                            doc = "Whether the effect shows its particles.")})
    public void effect(String effect, StarlarkInt seconds, StarlarkInt amplifier, boolean showParticles) {
        actions.effect(effect, seconds.toIntUnchecked(), amplifier.toIntUnchecked(), showParticles);
    }

    @StarlarkMethod(name = "clear_effects", doc = "Removes all status effects from the player.")
    public void clearEffects() {
        actions.clearEffects();
    }

    @StarlarkMethod(
            name = "give_xp",
            doc = "Grants the player experience points.",
            parameters = {@Param(name = "points", doc = "How many experience points to grant.")})
    public void giveXp(StarlarkInt points) {
        actions.giveXp(points.toIntUnchecked());
    }

    @StarlarkMethod(
            name = "set_gamemode",
            doc = "Sets the player's game mode: `survival`, `creative`, `adventure`, or `spectator`.",
            parameters = {@Param(name = "mode", doc = "The game mode name.")})
    public void setGamemode(String mode) {
        actions.setGamemode(mode);
    }

    @StarlarkMethod(
            name = "play_sound",
            doc = "Plays a sound at the player's position. Unknown sound ids are ignored.",
            parameters = {
                    @Param(name = "sound", doc = "The sound id, e.g. `\"minecraft:entity.player.levelup\"`."),
                    @Param(name = "volume", named = true, defaultValue = "1.0", doc = "Sound volume."),
                    @Param(name = "pitch", named = true, defaultValue = "1.0", doc = "Sound pitch.")})
    public void playSound(String sound, Object volume, Object pitch) throws EvalException {
        actions.playSound(sound, Nums.toDouble(volume), Nums.toDouble(pitch));
    }

    @StarlarkMethod(
            name = "title",
            doc = "Shows a large title on the player's screen (a string or a `text(...)` component). "
                    + "Set a `subtitle(...)` first if you want one beneath it.",
            parameters = {@Param(name = "message", doc = "The title text.")})
    public void title(Object message) {
        actions.title(MineText.coerce(message));
    }

    @StarlarkMethod(
            name = "subtitle",
            doc = "Sets the subtitle shown beneath the next `title(...)`.",
            parameters = {@Param(name = "message", doc = "The subtitle text.")})
    public void subtitle(Object message) {
        actions.subtitle(MineText.coerce(message));
    }

    @StarlarkMethod(
            name = "actionbar",
            doc = "Shows a message on the player's action bar (the strip above the hotbar).",
            parameters = {@Param(name = "message", doc = "The action-bar text.")})
    public void actionbar(Object message) {
        actions.actionbar(MineText.coerce(message));
    }

    @StarlarkMethod(
            name = "count",
            doc = "How many of an item the player is carrying.",
            parameters = {@Param(name = "item", doc = "The item id or a handle.")})
    public int count(Object item) {
        return actions.count(String.valueOf(item));
    }

    @StarlarkMethod(
            name = "has",
            doc = "Whether the player is carrying at least `count` of an item.",
            parameters = {
                    @Param(name = "item", doc = "The item id or a handle."),
                    @Param(name = "count", named = true, defaultValue = "1", doc = "The minimum amount."),
            })
    public boolean has(Object item, StarlarkInt count) {
        return actions.has(String.valueOf(item), count.toIntUnchecked());
    }

    @StarlarkMethod(
            name = "remove",
            doc = "Removes up to `count` of an item from the player's inventory; returns how many were removed.",
            parameters = {
                    @Param(name = "item", doc = "The item id or a handle."),
                    @Param(name = "count", named = true, defaultValue = "1", doc = "How many to remove."),
            })
    public int remove(Object item, StarlarkInt count) {
        return actions.remove(String.valueOf(item), count.toIntUnchecked());
    }

    @StarlarkMethod(name = "kill", doc = "Kills the player.")
    public void kill() {
        actions.kill();
    }

    @Override
    public String toString() {
        return name;
    }
}
