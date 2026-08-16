package ru.nelande.minelark.script;

/**
 * The game-side hooks the {@code client} namespace needs, implemented by the client adapter. Kept as
 * an interface so the {@code script} package stays free of Minecraft types (mirrors
 * {@link PlayerActions}). All reads are live: {@link #player()} / {@link #world()} return {@code null}
 * until the local player has entered a world, so a client script that runs at startup sees {@code None}
 * and a later tick/event callback sees the real player.
 */
public interface ClientAccess {
    /** The local player, or {@code null} when not in a world yet. */
    PlayerView player();

    /** The world the local player is in, or {@code null} when not in a world. */
    LevelView world();

    /** Sends a chat message (or a {@code /command} if it starts with a slash) to the server. */
    void sendChat(String message);

    /** Shows a message in the player's own chat locally, without sending anything to the server. */
    void showMessage(MineText message);
}
