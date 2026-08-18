package ru.nelande.minelark.script;

/**
 * The side-effecting things a {@link LevelView} can do, bridged to the real world by the game adapter.
 * Kept as an interface so the {@code script} package stays free of Minecraft types (mirrors
 * {@link PlayerActions}). Ids (blocks, entities, sounds, particles) are plain strings the adapter
 * resolves (bare id -> {@code minecraft:}); unknown ids are ignored.
 */
public interface LevelActions {

    /** Places the default state of block {@code blockId} at the given position. */
    void setBlock(int x, int y, int z, String blockId);

    /** Returns the id of the block at the given position (e.g. {@code minecraft:stone}). */
    String getBlock(int x, int y, int z);

    /** Spawns an entity of type {@code entityId} at the given position. */
    void spawn(String entityId, double x, double y, double z);

    /** Plays a sound at the given position. */
    void playSound(String soundId, double x, double y, double z, double volume, double pitch);

    /** Spawns {@code count} of a particle at the given position. */
    void spawnParticle(String particleId, double x, double y, double z, int count);

    /** Sets the time of day (in ticks). */
    void setTime(long ticks);

    /** Sets the weather: {@code "clear"}, {@code "rain"}, or {@code "thunder"}. */
    void setWeather(String kind);

    /** Creates an explosion at the given position. */
    void explode(double x, double y, double z, double power, boolean fire, boolean destroyBlocks);

    /** Strikes lightning at the given position. */
    void strikeLightning(double x, double y, double z);

    /** A sink that does nothing - for read-only contexts (tests, the client) and phases with no world. */
    LevelActions NOOP = new LevelActions() {
        @Override public void setBlock(int x, int y, int z, String blockId) {
        }

        @Override public String getBlock(int x, int y, int z) {
            return "minecraft:air";
        }

        @Override public void spawn(String entityId, double x, double y, double z) {
        }

        @Override public void playSound(String soundId, double x, double y, double z, double volume, double pitch) {
        }

        @Override public void spawnParticle(String particleId, double x, double y, double z, int count) {
        }

        @Override public void setTime(long ticks) {
        }

        @Override public void setWeather(String kind) {
        }

        @Override public void explode(double x, double y, double z, double power, boolean fire, boolean destroyBlocks) {
        }

        @Override public void strikeLightning(double x, double y, double z) {
        }
    };
}
