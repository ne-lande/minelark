package ru.nelande.minelark.script;

import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;

/**
 * Where a {@link HudElement}'s {@code x}/{@code y} are measured from. Corners inset toward the middle
 * of the screen; {@code CENTER} offsets from the middle. MC-agnostic - the client adapter turns an
 * anchor plus offsets and the text width into actual screen pixels.
 */
public enum HudAnchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER;

    /** Parses a script-facing name like {@code "top_left"}, or reports the valid choices. */
    static HudAnchor fromName(String name) throws EvalException {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw Starlark.errorf(
                    "unknown anchor '%s' (expected top_left, top_right, bottom_left, bottom_right, or center)",
                    name);
        }
    }
}
