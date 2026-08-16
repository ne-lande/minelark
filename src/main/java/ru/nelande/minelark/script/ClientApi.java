package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

/**
 * The {@code client} namespace for client scripts: read the local player and world, send chat, and
 * print local messages. Backed by a {@link ClientAccess} the adapter supplies. Values are read live,
 * so use them from tick/event callbacks (at startup there is no player yet, so {@code client.player}
 * is {@code None}).
 */
public final class ClientApi implements StarlarkValue {
    private final ClientAccess access;

    public ClientApi(ClientAccess access) {
        this.access = access;
    }

    @StarlarkMethod(
            name = "player",
            structField = true,
            doc = "The local player, or `None` if not in a world yet. Same view as `ctx.player` "
                    + "(name, position, health, held_item, level, tell).")
    public Object player() {
        PlayerView player = access.player();
        return player != null ? player : Starlark.NONE;
    }

    @StarlarkMethod(
            name = "world",
            structField = true,
            doc = "The world the local player is in, or `None` if not in a world (dimension, time, "
                    + "is_day, is_raining).")
    public Object world() {
        LevelView world = access.world();
        return world != null ? world : Starlark.NONE;
    }

    @StarlarkMethod(
            name = "send_chat",
            doc = "Sends a chat message to the server as if you typed it. If it starts with `/` it is "
                    + "sent as a command.",
            parameters = {@Param(name = "message", doc = "The message (or `/command`) to send.")})
    public void sendChat(String message) {
        access.sendChat(message);
    }

    @StarlarkMethod(
            name = "show_message",
            doc = "Shows a message in your own chat locally. Nothing is sent to the server - only you "
                    + "see it.",
            parameters = {@Param(name = "message", doc = "A string or a `text(...)` component.")})
    public void showMessage(Object message) {
        access.showMessage(MineText.coerce(message));
    }
}
