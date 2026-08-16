package ru.nelande.minelark.script;

import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * A namespace of events, e.g. {@code events.minelark}. Each event is exposed as a typed constant so
 * a typo fails fast ("no such field") rather than silently never firing. New core events are added
 * here as new {@code structField} accessors.
 */
public final class EventNamespace implements StarlarkValue {
    private final Events events;
    private final String namespace;

    EventNamespace(Events events, String namespace) {
        this.events = events;
        this.namespace = namespace;
    }

    @StarlarkMethod(
            name = "SERVER_STARTED",
            structField = true,
            doc = "Fires once when the server / world has finished loading.")
    public Event serverStarted() {
        return new Event(events, namespace + ":server_started");
    }
}
