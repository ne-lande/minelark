package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;

/**
 * A tiny standard library, available as top-level builtins in <b>every</b> phase (and the console).
 * It fills gaps Starlark itself leaves: a top-level guard (Starlark forbids a bare {@code if} at the
 * top level of a script), and a few number/colour helpers that come up constantly in HUD and content
 * work. MC-agnostic. Added to the environment by {@link StarlarkHost}.
 */
public final class PreludeApi {

    @StarlarkMethod(
            name = "require",
            doc = "Fails the script with `message` unless `condition` is truthy. Use it as a top-level "
                    + "guard - Starlark does not allow a bare `if` at the top level of a script.",
            parameters = {
                    @Param(name = "condition", doc = "The condition that must hold."),
                    @Param(name = "message", named = true, defaultValue = "\"requirement not met\"",
                            doc = "What to report if the condition is false."),
            })
    public void require(Object condition, String message) throws EvalException {
        if (!Starlark.truth(condition)) {
            throw Starlark.errorf("%s", message);
        }
    }

    @StarlarkMethod(
            name = "clamp",
            doc = "Constrains `value` to the range `[min, max]`. Returns an int if all three are ints, "
                    + "else a float.",
            parameters = {
                    @Param(name = "value", doc = "The number to constrain."),
                    @Param(name = "min", doc = "The lower bound."),
                    @Param(name = "max", doc = "The upper bound."),
            })
    public Object clamp(Object value, Object min, Object max) throws EvalException {
        double result = Math.max(toDouble(min), Math.min(toDouble(value), toDouble(max)));
        if (value instanceof StarlarkInt && min instanceof StarlarkInt && max instanceof StarlarkInt) {
            return StarlarkInt.of((long) result);
        }
        return StarlarkFloat.of(result);
    }

    @StarlarkMethod(
            name = "lerp",
            doc = "Linearly interpolates from `a` to `b` by `t` (0.0 = a, 1.0 = b). Returns a float.",
            parameters = {
                    @Param(name = "a", doc = "The value at t = 0."),
                    @Param(name = "b", doc = "The value at t = 1."),
                    @Param(name = "t", doc = "The blend factor."),
            })
    public StarlarkFloat lerp(Object a, Object b, Object t) throws EvalException {
        double from = toDouble(a);
        double to = toDouble(b);
        return StarlarkFloat.of(from + (to - from) * toDouble(t));
    }

    @StarlarkMethod(
            name = "rgb",
            doc = "Builds a `#rrggbb` colour string from red, green, and blue channels (each 0-255).",
            parameters = {
                    @Param(name = "red", doc = "Red, 0-255."),
                    @Param(name = "green", doc = "Green, 0-255."),
                    @Param(name = "blue", doc = "Blue, 0-255."),
            })
    public String rgb(StarlarkInt red, StarlarkInt green, StarlarkInt blue) throws EvalException {
        return String.format("#%02x%02x%02x", channel(red), channel(green), channel(blue));
    }

    private static int channel(StarlarkInt value) throws EvalException {
        int channel = value.toIntUnchecked();
        if (channel < 0 || channel > 255) {
            throw Starlark.errorf("colour channel %d is out of range 0-255", channel);
        }
        return channel;
    }

    private static double toDouble(Object value) throws EvalException {
        if (value instanceof StarlarkInt i) {
            return i.toIntUnchecked();
        }
        if (value instanceof StarlarkFloat f) {
            return f.toDouble();
        }
        throw Starlark.errorf("expected a number, got %s", Starlark.type(value));
    }
}
