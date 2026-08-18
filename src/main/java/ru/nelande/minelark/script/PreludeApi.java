package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;

import java.util.Random;

/**
 * A tiny standard library, available as top-level builtins in <b>every</b> phase (and the console).
 * It fills gaps Starlark itself leaves: a top-level guard (Starlark forbids a bare {@code if} at the
 * top level of a script), and a few number/colour/json/random helpers that come up constantly in HUD,
 * content, and gameplay work. MC-agnostic. Added to the environment by {@link StarlarkHost}.
 */
public final class PreludeApi {

    /** Backs {@code rand}/{@code rand_int}/{@code choice}. One per phase run (deterministic seeding is not offered). */
    private final Random random = new Random();

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

    @StarlarkMethod(
            name = "dist",
            doc = "The straight-line distance between two points `(x1, y1, z1)` and `(x2, y2, z2)`.",
            parameters = {
                    @Param(name = "x1", doc = "First point x."),
                    @Param(name = "y1", doc = "First point y."),
                    @Param(name = "z1", doc = "First point z."),
                    @Param(name = "x2", doc = "Second point x."),
                    @Param(name = "y2", doc = "Second point y."),
                    @Param(name = "z2", doc = "Second point z."),
            })
    public StarlarkFloat dist(Object x1, Object y1, Object z1, Object x2, Object y2, Object z2)
            throws EvalException {
        double dx = toDouble(x2) - toDouble(x1);
        double dy = toDouble(y2) - toDouble(y1);
        double dz = toDouble(z2) - toDouble(z1);
        return StarlarkFloat.of(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    @StarlarkMethod(
            name = "to_json",
            doc = "Serialises a value (dict, list, string, number, bool, `None`) to a JSON string.",
            parameters = {@Param(name = "value", doc = "The value to serialise.")})
    public String toJson(Object value) throws EvalException {
        return StarlarkJson.toJsonString(value);
    }

    @StarlarkMethod(
            name = "from_json",
            doc = "Parses a JSON string back into a value (dict, list, string, number, bool, `None`).",
            parameters = {@Param(name = "text", doc = "The JSON text to parse.")})
    public Object fromJson(String text) {
        return StarlarkJson.fromJsonString(text);
    }

    @StarlarkMethod(
            name = "rand",
            doc = "A random float in `[0.0, 1.0)`. Note: random values are not deterministic across runs.")
    public StarlarkFloat rand() {
        return StarlarkFloat.of(random.nextDouble());
    }

    @StarlarkMethod(
            name = "rand_int",
            doc = "A random integer between `min` and `max`, inclusive.",
            parameters = {
                    @Param(name = "min", doc = "The lowest possible value."),
                    @Param(name = "max", doc = "The highest possible value."),
            })
    public StarlarkInt randInt(StarlarkInt min, StarlarkInt max) throws EvalException {
        int lo = min.toIntUnchecked();
        int hi = max.toIntUnchecked();
        if (lo > hi) {
            throw Starlark.errorf("rand_int: min %d is greater than max %d", lo, hi);
        }
        return StarlarkInt.of(lo + random.nextInt(hi - lo + 1));
    }

    @StarlarkMethod(
            name = "choice",
            doc = "Returns a random element from a non-empty list (or other sequence).",
            parameters = {@Param(name = "seq", doc = "The sequence to pick from.")})
    public Object choice(Sequence<?> seq) throws EvalException {
        if (seq.isEmpty()) {
            throw Starlark.errorf("choice: cannot pick from an empty sequence");
        }
        return seq.get(random.nextInt(seq.size()));
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
