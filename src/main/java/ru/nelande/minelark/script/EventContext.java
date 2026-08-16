package ru.nelande.minelark.script;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.eval.Structure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The {@code ctx} passed to an event callback: a small mutable event object. It carries the event id
 * plus whatever per-event data the firing event supplies, exposed as read-only Starlark fields:
 *
 * <pre>{@code
 * def on_break(ctx):
 *     log.info(ctx.player.name + " broke " + ctx.block + " at " + str(ctx.x))
 *     if ctx.player.name == "Steve":
 *         ctx.cancel()          # cancellable events only
 * }</pre>
 *
 * <p>Data fields (e.g. {@code ctx.player}, {@code ctx.block}, {@code ctx.x}) are read via the
 * {@link Structure} interface. A cancellable event exposes {@code ctx.cancel()} and a readable /
 * writable {@code ctx.cancelled}. An editable field (declared per event) can be reassigned with
 * plain attribute assignment, e.g. {@code ctx.message = "hi"}. The three reserved fields
 * {@code event}, {@code cancellable}, and {@code cancelled} are always present.
 */
public final class EventContext implements Structure {
    private static final Set<String> RESERVED = Set.of("event", "cancellable", "cancelled");

    private final String id;
    private final Map<String, Object> data;
    private final Set<String> editable;
    private final boolean cancellable;
    private boolean cancelled;

    /** An event with no extra data and no cancellation (e.g. {@code server_started}). */
    public EventContext(String id) {
        this(id, Map.of(), Set.of(), false);
    }

    /**
     * @param id          the namespaced event id
     * @param data        per-event fields (values are plain Java/Starlark values; converted on read)
     * @param editable    the subset of {@code data} keys a callback may reassign
     * @param cancellable whether {@code cancel()} / {@code cancelled = True} are permitted
     */
    public EventContext(String id, Map<String, Object> data, Set<String> editable, boolean cancellable) {
        this.id = id;
        this.data = new LinkedHashMap<>(data);
        this.editable = Set.copyOf(editable);
        this.cancellable = cancellable;
    }

    // --- Starlark method members (checked before Structure fields) ---

    @StarlarkMethod(
            name = "cancel",
            doc = "Cancels this event (cancellable events only), stopping the default action. Raises "
                    + "if the event cannot be cancelled.")
    public void cancel() throws EvalException {
        requireCancellable("cancel()");
        cancelled = true;
    }

    // --- Structure (dynamic per-event fields) ---

    @Override
    public Object getValue(String name) throws EvalException {
        return switch (name) {
            case "event" -> id;
            case "cancellable" -> cancellable;
            case "cancelled" -> cancelled;
            default -> data.containsKey(name) ? Starlark.fromJava(data.get(name), null) : null;
        };
    }

    @Override
    public ImmutableCollection<String> getFieldNames() {
        ImmutableList.Builder<String> names = ImmutableList.builder();
        names.addAll(RESERVED);
        names.addAll(data.keySet());
        return names.build();
    }

    @Override
    public String getErrorMessageForUnknownField(String name) {
        return "event " + id + " has no field '" + name + "'";
    }

    @Override
    public void setField(String name, Object value) throws EvalException {
        if (name.equals("cancelled")) {
            requireCancellable("cancelled");
            cancelled = Starlark.truth(value);
            return;
        }
        if (editable.contains(name)) {
            data.put(name, value);
            return;
        }
        if (RESERVED.contains(name) || data.containsKey(name)) {
            throw new EvalException("field '" + name + "' of event " + id + " is read-only");
        }
        throw new EvalException(getErrorMessageForUnknownField(name));
    }

    private void requireCancellable(String what) throws EvalException {
        if (!cancellable) {
            throw new EvalException("event " + id + " is not cancellable (" + what + ")");
        }
    }

    // --- Java-side read-back, used by the game adapter after firing ---

    /** Whether a callback cancelled this event. */
    public boolean isCancelled() {
        return cancelled;
    }

    /** The current (possibly edited) value of a data field, or {@code null} if absent. */
    public Object field(String name) {
        return data.get(name);
    }

    /** The current value of a field coerced to a string (for editable text fields like {@code message}). */
    public String editedString(String name) {
        Object value = data.get(name);
        if (value == null) {
            return null;
        }
        return value instanceof String s ? s : Starlark.str(value);
    }

    @Override
    public String toString() {
        return id;
    }
}
