package ru.nelande.minelark.script;

/**
 * The side-effecting things an {@link EntityView} can do (e.g. on {@code ctx.attacker}), bridged to the
 * real entity by the game adapter. Kept as an interface so the {@code script} package stays free of
 * Minecraft types (mirrors {@link PlayerActions}). Non-living entities ignore the living-only verbs.
 */
public interface EntityActions {

    /** Removes the entity from the world. */
    void kill();

    /**
     * Applies a status effect to a living entity. {@code amplifier} is the MC-native level (0 = level I).
     * Unknown effect ids, and non-living entities, are ignored.
     */
    void effect(String effectId, int seconds, int amplifier, boolean showParticles);

    /** Teleports the entity to the given coordinates within its current world. */
    void teleport(double x, double y, double z);

    /** Deals {@code amount} of generic damage to a living entity. */
    void damage(double amount);

    /** A sink that does nothing - for read-only contexts (tests, the client). */
    EntityActions NOOP = new EntityActions() {
        @Override public void kill() {
        }

        @Override public void effect(String effectId, int seconds, int amplifier, boolean showParticles) {
        }

        @Override public void teleport(double x, double y, double z) {
        }

        @Override public void damage(double amount) {
        }
    };
}
