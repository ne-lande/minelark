package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/**
 * A single subscribable event (e.g. {@code events.minelark.SERVER_STARTED}). Treat it as a
 * collection of listeners: {@code .on}/{@code .add} to subscribe, {@code .remove} to unsubscribe,
 * {@code .list} to inspect. Callbacks receive the event {@code ctx}.
 */
public final class Event implements StarlarkValue {
    private final Events events;
    private final String id;

    Event(Events events, String id) {
        this.events = events;
        this.id = id;
    }

    @StarlarkMethod(
            name = "on",
            doc = "Subscribes a callback `def handler(ctx): ...` to this event. Alias of `add`.",
            parameters = {@Param(name = "callback", doc = "The handler function.")})
    public void on(Object callback) throws EvalException {
        add(callback);
    }

    @StarlarkMethod(
            name = "add",
            doc = "Subscribes a callback to this event (same as `on`).",
            parameters = {@Param(name = "callback", doc = "The handler function.")})
    public void add(Object callback) throws EvalException {
        events.register(id, asCallable(callback, "add"));
    }

    @StarlarkMethod(
            name = "remove",
            doc = "Unsubscribes a previously-added callback (matched by identity). Returns whether one was removed.",
            parameters = {@Param(name = "callback", doc = "The same function passed to on/add.")})
    public boolean remove(Object callback) throws EvalException {
        return events.unregister(id, asCallable(callback, "remove"));
    }

    @StarlarkMethod(name = "list", doc = "Returns the callbacks currently subscribed to this event.")
    public StarlarkList<?> list() {
        return StarlarkList.immutableCopyOf(events.listeners(id));
    }

    private StarlarkCallable asCallable(Object callback, String method) throws EvalException {
        if (callback instanceof StarlarkCallable function) {
            return function;
        }
        throw new EvalException(id + "." + method + "() expects a function");
    }

    @Override
    public String toString() {
        return id;
    }
}
