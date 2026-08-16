package ru.nelande.minelark.script;

import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * A read-only view of a world / dimension, handed to event callbacks as {@code ctx.level} (and as
 * {@code ctx.player.level}). MC-agnostic: the game adapter fills it from a {@code World}.
 */
public final class LevelView implements StarlarkValue {
    private final String dimension;
    private final long time;
    private final boolean day;
    private final boolean raining;

    public LevelView(String dimension, long time, boolean day, boolean raining) {
        this.dimension = dimension;
        this.time = time;
        this.day = day;
        this.raining = raining;
    }

    @StarlarkMethod(name = "dimension", structField = true, doc = "The dimension id, e.g. `minecraft:overworld`.")
    public String dimension() {
        return dimension;
    }

    @StarlarkMethod(name = "time", structField = true, doc = "The dimension's time of day, in ticks.")
    public long time() {
        return time;
    }

    @StarlarkMethod(name = "is_day", structField = true, doc = "Whether it is currently daytime.")
    public boolean isDay() {
        return day;
    }

    @StarlarkMethod(name = "is_raining", structField = true, doc = "Whether it is currently raining.")
    public boolean isRaining() {
        return raining;
    }

    @Override
    public String toString() {
        return dimension;
    }
}
