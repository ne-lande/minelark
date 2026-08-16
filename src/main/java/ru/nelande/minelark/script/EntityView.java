package ru.nelande.minelark.script;

import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * A read-only view of a (non-player) entity, e.g. {@code ctx.attacker} on a death event. MC-agnostic:
 * the game adapter fills it from an {@code Entity}. Players get the richer {@link PlayerView}.
 */
public final class EntityView implements StarlarkValue {
    private final String type;
    private final String uuid;
    private final String name;
    private final double x;
    private final double y;
    private final double z;
    private final LevelView level;

    public EntityView(String type, String uuid, String name, double x, double y, double z, LevelView level) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.level = level;
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

    @Override
    public String toString() {
        return type;
    }
}
