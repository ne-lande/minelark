package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The server-side {@code net} namespace: send named-channel messages to clients and react to messages
 * from them. This is the bridge that lets server scripts push data (including their own "events") out
 * to client scripts. Messages carry any JSON-able value; the matching {@code net} namespace on the
 * client has {@code send} (to the server) and {@code on}. MC-agnostic - the adapter supplies a
 * {@link ServerNetwork} to actually put packets on the wire.
 */
public final class ServerNetworkApi implements StarlarkValue {
    private static final Pattern CHANNEL = Pattern.compile("[a-zA-Z0-9_./:-]+");

    private final ServerNetwork sender;
    private final Log log;
    private final Map<String, List<StarlarkCallable>> handlers = new LinkedHashMap<>();

    public ServerNetworkApi(ServerNetwork sender, Log log) {
        this.sender = sender;
        this.log = log;
    }

    @StarlarkMethod(
            name = "send",
            doc = "Sends a message to one player on a named channel. `data` is any JSON-able value "
                    + "(dict, list, string, number, bool, `None`), delivered to that player's client "
                    + "scripts listening on the same channel with `net.on`.",
            parameters = {
                    @Param(name = "player", doc = "A player (e.g. `ctx.player`) or their uuid string."),
                    @Param(name = "channel", doc = "The channel name both sides agree on."),
                    @Param(name = "data", doc = "The payload to send (must be JSON-able)."),
            })
    public void send(Object player, String channel, Object data) throws EvalException {
        sender.sendToPlayer(playerUuid(player), validChannel(channel), StarlarkJson.toJsonString(data));
    }

    @StarlarkMethod(
            name = "broadcast",
            doc = "Sends a message on a channel to every connected player's client scripts.",
            parameters = {
                    @Param(name = "channel", doc = "The channel name both sides agree on."),
                    @Param(name = "data", doc = "The payload to send (must be JSON-able)."),
            })
    public void broadcast(String channel, Object data) throws EvalException {
        sender.broadcast(validChannel(channel), StarlarkJson.toJsonString(data));
    }

    @StarlarkMethod(
            name = "on",
            doc = "Registers a handler for messages clients send on `channel` (with the client's "
                    + "`net.send`). The handler gets a `ctx` with `ctx.channel`, `ctx.data` (the "
                    + "decoded value), and `ctx.player` (who sent it).",
            parameters = {
                    @Param(name = "channel", doc = "The channel to listen on."),
                    @Param(name = "handler", doc = "A function taking one `ctx` argument."),
            })
    public void on(String channel, StarlarkCallable handler) throws EvalException {
        handlers.computeIfAbsent(validChannel(channel), k -> new ArrayList<>()).add(handler);
    }

    /** Whether any handler listens on {@code channel} - lets the adapter skip decoding a stray packet. */
    public boolean hasListeners(String channel) {
        List<StarlarkCallable> list = handlers.get(channel);
        return list != null && !list.isEmpty();
    }

    /** Decodes an incoming client message and fires the handlers for its channel with a fresh ctx. */
    public void dispatch(String channel, String json, PlayerView player, ScriptLog sink) {
        List<StarlarkCallable> list = handlers.get(channel);
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", channel);
        data.put("data", StarlarkJson.fromJsonString(json));
        if (player != null) {
            data.put("player", player);
        }
        EventContext ctx = new EventContext("net:" + channel, data, Set.of(), false);
        ScriptCallbacks.fire("net:" + channel, List.copyOf(list), ctx, log, sink);
    }

    private static String validChannel(String channel) throws EvalException {
        if (!CHANNEL.matcher(channel).matches()) {
            throw Starlark.errorf("invalid channel '%s' (letters, digits, and _ . / : - only)", channel);
        }
        return channel;
    }

    private static String playerUuid(Object player) throws EvalException {
        if (player instanceof PlayerView view) {
            return view.uuid();
        }
        if (player instanceof String uuid) {
            return uuid;
        }
        throw Starlark.errorf("net.send expects a player or a uuid string, got %s", Starlark.type(player));
    }
}
