package ru.nelande.minelark.script;

import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * The {@code ctx} passed to an event callback. Minimal today (just the event id); event-specific
 * fields (player, position, cancellation, ...) will be added per event as richer events land.
 */
public final class EventContext implements StarlarkValue {
    private final String id;

    EventContext(String id) {
        this.id = id;
    }

    @StarlarkMethod(name = "event", structField = true, doc = "The namespaced id of the event that fired.")
    public String event() {
        return id;
    }
}
