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
 * The client-side {@code net} namespace: send named-channel messages to the server and react to
 * messages the server sends. The mirror of {@link ServerNetworkApi}; together they let a pack's server
 * and client scripts talk. Messages carry any JSON-able value. MC-agnostic - the adapter supplies a
 * {@link ClientNetwork} to put packets on the wire.
 */
public final class ClientNetworkApi implements StarlarkValue {
    private static final Pattern CHANNEL = Pattern.compile("[a-zA-Z0-9_./:-]+");

    private final ClientNetwork sender;
    private final Log log;
    private final Map<String, List<StarlarkCallable>> handlers = new LinkedHashMap<>();

    public ClientNetworkApi(ClientNetwork sender, Log log) {
        this.sender = sender;
        this.log = log;
    }

    @StarlarkMethod(
            name = "send",
            doc = "Sends a message to the server on a named channel. `data` is any JSON-able value, "
                    + "delivered to the server scripts listening on the same channel with `net.on`.",
            parameters = {
                    @Param(name = "channel", doc = "The channel name both sides agree on."),
                    @Param(name = "data", doc = "The payload to send (must be JSON-able)."),
            })
    public void send(String channel, Object data) throws EvalException {
        sender.sendToServer(validChannel(channel), StarlarkJson.toJsonString(data));
    }

    @StarlarkMethod(
            name = "on",
            doc = "Registers a handler for messages the server sends on `channel` (with the server's "
                    + "`net.send`/`net.broadcast`). The handler gets a `ctx` with `ctx.channel` and "
                    + "`ctx.data` (the decoded value).",
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

    /** Decodes an incoming server message and fires the handlers for its channel with a fresh ctx. */
    public void dispatch(String channel, String json, ScriptLog sink) {
        List<StarlarkCallable> list = handlers.get(channel);
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", channel);
        data.put("data", StarlarkJson.fromJsonString(json));
        EventContext ctx = new EventContext("net:" + channel, data, Set.of(), false);
        ScriptCallbacks.fire("net:" + channel, List.copyOf(list), ctx, log, sink);
    }

    private static String validChannel(String channel) throws EvalException {
        if (!CHANNEL.matcher(channel).matches()) {
            throw Starlark.errorf("invalid channel '%s' (letters, digits, and _ . / : - only)", channel);
        }
        return channel;
    }
}
