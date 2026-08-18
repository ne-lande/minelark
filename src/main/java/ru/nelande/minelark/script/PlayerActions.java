package ru.nelande.minelark.script;

/**
 * The side-effecting things a {@link PlayerView} can do, bridged to the real player by the game
 * adapter. Kept as an interface so the {@code script} package stays free of Minecraft types and so
 * tests can supply a capturing fake.
 */
public interface PlayerActions {

    /** Sends a system message to the player. */
    void tell(MineText message);

    /** Gives the player {@code count} of the item with id {@code itemId} (ignored if unknown). */
    void give(String itemId, int count);

    /** Teleports the player to the given coordinates within their current world. */
    void teleport(double x, double y, double z);

    // The verbs below default to no-ops so existing bridges (the test convenience ctor, the client
    // adapter) keep compiling; the server adapter overrides them with real behaviour.

    /** Restores the player to full health. */
    default void heal() {
    }

    /** Sets the player's health (clamped to their max by the adapter). */
    default void setHealth(double health) {
    }

    /** Deals {@code amount} of generic damage to the player. */
    default void damage(double amount) {
    }

    /**
     * Applies a status effect. {@code amplifier} is the MC-native level (0 = level I). Unknown effect
     * ids are ignored.
     */
    default void effect(String effectId, int seconds, int amplifier, boolean showParticles) {
    }

    /** Removes all status effects from the player. */
    default void clearEffects() {
    }

    /** Grants the player {@code points} experience points. */
    default void giveXp(int points) {
    }

    /** Sets the player's game mode (survival/creative/adventure/spectator); unknown names are ignored. */
    default void setGamemode(String mode) {
    }

    /** Plays a sound at the player's position. Unknown sound ids are ignored. */
    default void playSound(String soundId, double volume, double pitch) {
    }

    /** Shows a large title on the player's screen. */
    default void title(MineText message) {
    }

    /** Sets the subtitle shown beneath the next {@link #title}. */
    default void subtitle(MineText message) {
    }

    /** Shows a message on the player's action bar (above the hotbar). */
    default void actionbar(MineText message) {
    }

    /** How many of the item with id {@code itemId} the player is carrying. */
    default int count(String itemId) {
        return 0;
    }

    /** Whether the player is carrying at least {@code count} of the item with id {@code itemId}. */
    default boolean has(String itemId, int count) {
        return false;
    }

    /** Removes up to {@code count} of the item from the player's inventory; returns how many were removed. */
    default int remove(String itemId, int count) {
        return 0;
    }

    /** Kills the player. */
    default void kill() {
    }
}
