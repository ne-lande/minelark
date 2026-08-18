package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

/**
 * The {@code client} namespace as exposed to <b>server-pushed</b> scripts that were granted the
 * {@link Capability#CLIENT_READ} capability but not {@link Capability#CHAT}: it can read the local
 * player and world and show local messages, but deliberately has <b>no {@code send_chat}</b> - a
 * pushed script must never be able to run commands or send chat as the player. When {@code CHAT} is
 * granted the full {@link ClientApi} is used instead.
 *
 * <p>Because Starlark reflects every {@code @StarlarkMethod} on the object it is given, dropping a
 * method means dropping it from the class - hence this read-only twin rather than a runtime flag.
 */
public final class ReadOnlyClientApi implements StarlarkValue {
    private final ClientAccess access;

    public ReadOnlyClientApi(ClientAccess access) {
        this.access = access;
    }

    @StarlarkMethod(
            name = "player",
            structField = true,
            doc = "The local player, or `None` if not in a world yet (name, position, health, "
                    + "held_item, level, tell).")
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
            name = "show_message",
            doc = "Shows a message in your own chat locally. Nothing is sent to the server - only you "
                    + "see it.",
            parameters = {@Param(name = "message", doc = "A string or a `text(...)` component.")})
    public void showMessage(Object message) {
        access.showMessage(MineText.coerce(message));
    }
}
