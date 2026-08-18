package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

/**
 * A view of a (non-player) entity, e.g. {@code ctx.attacker} on a death event. Read its identity and
 * position, and act on it (kill, effect, teleport, damage). MC-agnostic: the game adapter fills the
 * data from an {@code Entity} and bridges the actions through {@link EntityActions}. Players get the
 * richer {@link PlayerView}.
 */
public final class EntityView implements StarlarkValue {
    private final String type;
    private final String uuid;
    private final String name;
    private final double x;
    private final double y;
    private final double z;
    private final LevelView level;
    private final EntityActions actions;

    /** A read-only view (actions are no-ops) - for tests and the client. */
    public EntityView(String type, String uuid, String name, double x, double y, double z, LevelView level) {
        this(type, uuid, name, x, y, z, level, EntityActions.NOOP);
    }

    public EntityView(String type, String uuid, String name, double x, double y, double z, LevelView level,
                      EntityActions actions) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.level = level;
        this.actions = actions;
    }

    @StarlarkMethod(name = "type", structField = true, doc = "The entity type id, e.g. `minecraft:creeper`.")
    public String type() {
        return type;
    }

    @StarlarkMethod(name = "uuid", structField = true, doc = "The entity's UUID as a string.")
    public String uuid() {
        return uuid;
    }

    @StarlarkMethod(name = "name", structField = true, doc = "The entity's display name.")
    public String name() {
        return name;
    }

    @StarlarkMethod(name = "x", structField = true, doc = "The entity's x position.")
    public double x() {
        return x;
    }

    @StarlarkMethod(name = "y", structField = true, doc = "The entity's y position.")
    public double y() {
        return y;
    }

    @StarlarkMethod(name = "z", structField = true, doc = "The entity's z position.")
    public double z() {
        return z;
    }

    @StarlarkMethod(name = "level", structField = true, doc = "The world the entity is in.")
    public LevelView level() {
        return level;
    }

    // --- actions ---

    @StarlarkMethod(name = "kill", doc = "Removes the entity from the world.")
    public void kill() {
        actions.kill();
    }

    @StarlarkMethod(
            name = "effect",
            doc = "Applies a status effect to a living entity. `amplifier` is the MC level (0 = level I). "
                    + "Unknown effect ids, and non-living entities, are ignored.",
            parameters = {
                    @Param(name = "effect", doc = "The effect id, e.g. `\"slowness\"`."),
                    @Param(name = "seconds", named = true, defaultValue = "30", doc = "Duration in seconds."),
                    @Param(name = "amplifier", named = true, defaultValue = "0", doc = "Level, 0-based (0 = I)."),
                    @Param(name = "show_particles", named = true, defaultValue = "True",
                            doc = "Whether the effect shows its particles.")})
    public void effect(String effect, StarlarkInt seconds, StarlarkInt amplifier, boolean showParticles) {
        actions.effect(effect, seconds.toIntUnchecked(), amplifier.toIntUnchecked(), showParticles);
    }

    @StarlarkMethod(
            name = "teleport",
            doc = "Teleports the entity to the given coordinates within its current world.",
            parameters = {
                    @Param(name = "x", doc = "Target x."),
                    @Param(name = "y", doc = "Target y."),
                    @Param(name = "z", doc = "Target z.")})
    public void teleport(Object x, Object y, Object z) throws EvalException {
        actions.teleport(Nums.toDouble(x), Nums.toDouble(y), Nums.toDouble(z));
    }

    @StarlarkMethod(
            name = "damage",
            doc = "Deals damage to a living entity (2 per heart).",
            parameters = {@Param(name = "amount", doc = "How much damage to deal.")})
    public void damage(Object amount) throws EvalException {
        actions.damage(Nums.toDouble(amount));
    }

    @Override
    public String toString() {
        return type;
    }
}
