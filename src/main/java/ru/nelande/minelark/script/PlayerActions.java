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
}
