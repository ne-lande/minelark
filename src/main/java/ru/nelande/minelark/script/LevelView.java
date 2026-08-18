package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

/**
 * A view of a world / dimension, handed to event callbacks as {@code ctx.level} (and as
 * {@code ctx.player.level}). Read its dimension, time, and weather; and act on it (place blocks, spawn
 * entities, play sounds/particles, set time/weather, explode, strike lightning). MC-agnostic: the game
 * adapter fills the data from a {@code World} and bridges the actions through {@link LevelActions}.
 */
public final class LevelView implements StarlarkValue {
    private final String dimension;
    private final long time;
    private final boolean day;
    private final boolean raining;
    private final LevelActions actions;

    /** A read-only view (actions are no-ops) - for tests, the client, and phases with no live world. */
    public LevelView(String dimension, long time, boolean day, boolean raining) {
        this(dimension, time, day, raining, LevelActions.NOOP);
    }

    public LevelView(String dimension, long time, boolean day, boolean raining, LevelActions actions) {
        this.dimension = dimension;
        this.time = time;
        this.day = day;
        this.raining = raining;
        this.actions = actions;
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

    // --- actions ---

    @StarlarkMethod(
            name = "set_block",
            doc = "Places the default state of a block at the given position. Unknown ids are ignored.",
            parameters = {
                    @Param(name = "x", doc = "Block x."),
                    @Param(name = "y", doc = "Block y."),
                    @Param(name = "z", doc = "Block z."),
                    @Param(name = "block", doc = "The block id, e.g. `\"minecraft:stone\"` or a handle.")})
    public void setBlock(Object x, Object y, Object z, Object block) throws EvalException {
        actions.setBlock(Nums.toInt(x), Nums.toInt(y), Nums.toInt(z), String.valueOf(block));
    }

    @StarlarkMethod(
            name = "get_block",
            doc = "Returns the id of the block at the given position, e.g. `minecraft:stone`.",
            parameters = {
                    @Param(name = "x", doc = "Block x."),
                    @Param(name = "y", doc = "Block y."),
                    @Param(name = "z", doc = "Block z.")})
    public String getBlock(Object x, Object y, Object z) throws EvalException {
        return actions.getBlock(Nums.toInt(x), Nums.toInt(y), Nums.toInt(z));
    }

    @StarlarkMethod(
            name = "spawn",
            doc = "Spawns an entity at the given position. Unknown ids are ignored.",
            parameters = {
                    @Param(name = "entity", doc = "The entity type id, e.g. `\"minecraft:zombie\"`."),
                    @Param(name = "x", doc = "Spawn x."),
                    @Param(name = "y", doc = "Spawn y."),
                    @Param(name = "z", doc = "Spawn z.")})
    public void spawn(String entity, Object x, Object y, Object z) throws EvalException {
        actions.spawn(entity, Nums.toDouble(x), Nums.toDouble(y), Nums.toDouble(z));
    }

    @StarlarkMethod(
            name = "play_sound",
            doc = "Plays a sound at the given position. Unknown sound ids are ignored.",
            parameters = {
                    @Param(name = "sound", doc = "The sound id, e.g. `\"minecraft:entity.lightning_bolt.thunder\"`."),
                    @Param(name = "x", doc = "Sound x."),
                    @Param(name = "y", doc = "Sound y."),
                    @Param(name = "z", doc = "Sound z."),
                    @Param(name = "volume", named = true, defaultValue = "1.0", doc = "Sound volume."),
                    @Param(name = "pitch", named = true, defaultValue = "1.0", doc = "Sound pitch.")})
    public void playSound(String sound, Object x, Object y, Object z, Object volume, Object pitch)
            throws EvalException {
        actions.playSound(sound, Nums.toDouble(x), Nums.toDouble(y), Nums.toDouble(z),
                Nums.toDouble(volume), Nums.toDouble(pitch));
    }

    @StarlarkMethod(
            name = "spawn_particle",
            doc = "Spawns a particle at the given position. Unknown ids are ignored.",
            parameters = {
                    @Param(name = "particle", doc = "The particle id, e.g. `\"minecraft:heart\"`."),
                    @Param(name = "x", doc = "Particle x."),
                    @Param(name = "y", doc = "Particle y."),
                    @Param(name = "z", doc = "Particle z."),
                    @Param(name = "count", named = true, defaultValue = "1", doc = "How many to spawn.")})
    public void spawnParticle(String particle, Object x, Object y, Object z, StarlarkInt count)
            throws EvalException {
        actions.spawnParticle(particle, Nums.toDouble(x), Nums.toDouble(y), Nums.toDouble(z),
                count.toIntUnchecked());
    }

    @StarlarkMethod(
            name = "set_time",
            doc = "Sets the world's time of day, in ticks (0 = dawn, 6000 = noon, 18000 = midnight).",
            parameters = {@Param(name = "ticks", doc = "The new time, in ticks.")})
    public void setTime(StarlarkInt ticks) {
        actions.setTime(ticks.toIntUnchecked());
    }

    @StarlarkMethod(
            name = "set_weather",
            doc = "Sets the weather: `clear`, `rain`, or `thunder`.",
            parameters = {@Param(name = "kind", doc = "The weather to set.")})
    public void setWeather(String kind) {
        actions.setWeather(kind);
    }

    @StarlarkMethod(
            name = "explode",
            doc = "Creates an explosion at the given position.",
            parameters = {
                    @Param(name = "x", doc = "Explosion x."),
                    @Param(name = "y", doc = "Explosion y."),
                    @Param(name = "z", doc = "Explosion z."),
                    @Param(name = "power", named = true, defaultValue = "4.0",
                            doc = "Explosion power (TNT is 4)."),
                    @Param(name = "fire", named = true, defaultValue = "False",
                            doc = "Whether the explosion sets fires."),
                    @Param(name = "destroy_blocks", named = true, defaultValue = "True",
                            doc = "Whether the explosion breaks blocks.")})
    public void explode(Object x, Object y, Object z, Object power, boolean fire, boolean destroyBlocks)
            throws EvalException {
        actions.explode(Nums.toDouble(x), Nums.toDouble(y), Nums.toDouble(z),
                Nums.toDouble(power), fire, destroyBlocks);
    }

    @StarlarkMethod(
            name = "strike_lightning",
            doc = "Strikes lightning at the given position.",
            parameters = {
                    @Param(name = "x", doc = "Strike x."),
                    @Param(name = "y", doc = "Strike y."),
                    @Param(name = "z", doc = "Strike z.")})
    public void strikeLightning(Object x, Object y, Object z) throws EvalException {
        actions.strikeLightning(Nums.toDouble(x), Nums.toDouble(y), Nums.toDouble(z));
    }

    @Override
    public String toString() {
        return dimension;
    }
}
