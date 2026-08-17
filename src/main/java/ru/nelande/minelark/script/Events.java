package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Root of the {@code events} API for server scripts. Events are addressed by namespace, mirroring
 * Minecraft ids so different mods can never clash:
 *
 * <pre>{@code
 * events.minelark.SERVER_STARTED.on(handler)   # core events (namespace "minelark")
 * events.somemod.SOME_EVENT.on(handler)        # a mod's events (when integrated)
 * events.of("minelark:server_started").on(...)  # fallback: any event by raw id
 * }</pre>
 *
 * <p>Each callback receives a single {@code ctx} argument (the event context).
 */
public final class Events implements StarlarkValue {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /**
     * Which lifecycle phase these events belong to. Server and client events are addressed by the
     * same {@code events.minelark.*} constants, but a server script may only use server events and a
     * client script only client events - the wrong side would silently never fire (server events are
     * unreachable from a networked client), so it is rejected up front instead. See
     * {@link EventNamespace}.
     */
    public enum Scope {
        SERVER("server"),
        CLIENT("client");

        private final String folder;

        Scope(String folder) {
            this.folder = folder;
        }

        /** The script folder name for this phase, e.g. {@code "server"} - used in error messages. */
        public String folder() {
            return folder;
        }
    }

    private final Map<String, List<StarlarkCallable>> handlers = new LinkedHashMap<>();
    private final Log log;
    private final Scope scope;

    public Events(Log log, Scope scope) {
        this.log = log;
        this.scope = scope;
    }

    @StarlarkMethod(name = "minelark", structField = true, doc = "Minelark's own (core) events.")
    public EventNamespace minelark() {
        return new EventNamespace(this, "minelark", scope);
    }

    @StarlarkMethod(
            name = "of",
            doc = "Looks up an event by its full `namespace:path` id - for events not exposed as a named "
                    + "constant (e.g. contributed by another mod).",
            parameters = {@Param(name = "id", doc = "The event id, e.g. `\"minelark:server_started\"`.")})
    public Event of(String id) throws EvalException {
        String trimmed = id.trim();
        if (!ID.matcher(trimmed).matches()) {
            throw new EvalException("events.of() invalid event id '" + id + "' (expected namespace:path)");
        }
        return new Event(this, trimmed);
    }

    // --- registry, used by Event ---

    void register(String id, StarlarkCallable callback) {
        handlers.computeIfAbsent(id, key -> new ArrayList<>()).add(callback);
    }

    boolean unregister(String id, StarlarkCallable callback) {
        List<StarlarkCallable> list = handlers.get(id);
        return list != null && list.remove(callback);
    }

    List<StarlarkCallable> listeners(String id) {
        return handlers.getOrDefault(id, List.of());
    }

    /** Whether any callback is registered for {@code id} - lets the adapter skip firing hot events. */
    public boolean hasListeners(String id) {
        List<StarlarkCallable> list = handlers.get(id);
        return list != null && !list.isEmpty();
    }

    /** Invokes every callback registered for {@code id}, passing a fresh dataless {@link EventContext}. */
    public void fire(String id, ScriptLog sink) {
        fire(id, new EventContext(id), sink);
    }

    /**
     * Invokes every callback registered for {@code id}, passing the given {@code ctx}. The same
     * {@code ctx} instance is shared across the callbacks, so mutations (cancellation, edited fields)
     * accumulate and can be read back by the caller afterwards.
     */
    public void fire(String id, EventContext ctx, ScriptLog sink) {
        ScriptCallbacks.fire("event:" + id, List.copyOf(listeners(id)), ctx, log, sink);
    }
}
