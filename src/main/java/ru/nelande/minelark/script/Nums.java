package ru.nelande.minelark.script;

import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;

/**
 * Small numeric-coercion helpers shared by the view action verbs. Starlark does not auto-coerce an
 * {@code int} into a {@code double} parameter, so coordinate/amount params are declared as
 * {@code Object} and run through here - which lets a script pass either {@code 64} or {@code 64.0}
 * (see [[minelark-api-design]]). MC-agnostic.
 */
final class Nums {
    private Nums() {
    }

    /** Coerces a Starlark int or float to a double, or raises a clear error. */
    static double toDouble(Object value) throws EvalException {
        if (value instanceof StarlarkInt i) {
            return i.toIntUnchecked();
        }
        if (value instanceof StarlarkFloat f) {
            return f.toDouble();
        }
        throw Starlark.errorf("expected a number, got %s", Starlark.type(value));
    }

    /** Coerces a Starlark int or (whole) float to an int, or raises a clear error. */
    static int toInt(Object value) throws EvalException {
        if (value instanceof StarlarkInt i) {
            return i.toIntUnchecked();
        }
        if (value instanceof StarlarkFloat f) {
            return (int) f.toDouble();
        }
        throw Starlark.errorf("expected a number, got %s", Starlark.type(value));
    }
}
