package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code debug} namespace for client scripts: add your own lines to the F3 debug overlay. Each
 * line is keyed, so setting the same key again replaces it (handy from a per-tick callback). The
 * adapter reads {@link #lines()} while the overlay renders. MC-agnostic - the client and the F3
 * rendering thread are the same client thread, so a plain map is safe.
 */
public final class DebugApi implements StarlarkValue {
    private final Map<String, String> entries = new LinkedHashMap<>();

    @StarlarkMethod(
            name = "set",
            doc = "Adds or replaces a debug line. Shown on the F3 screen as `key: value`.",
            parameters = {
                    @Param(name = "key", doc = "A stable key identifying the line."),
                    @Param(name = "value", doc = "The text to show (call `str(...)` on non-strings).")})
    public void set(String key, String value) {
        entries.put(key, value);
    }

    @StarlarkMethod(
            name = "remove",
            doc = "Removes a debug line previously added with `set`. Returns whether one was removed.",
            parameters = {@Param(name = "key", doc = "The key passed to `set`.")})
    public boolean remove(String key) {
        return entries.remove(key) != null;
    }

    @StarlarkMethod(name = "clear", doc = "Removes all debug lines added by scripts.")
    public void clear() {
        entries.clear();
    }

    /** The current lines, formatted {@code "key: value"}, for the adapter to append to the F3 overlay. */
    public List<String> lines() {
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .toList();
    }
}
