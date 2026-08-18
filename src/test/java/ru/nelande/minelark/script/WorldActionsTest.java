package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the "act on the world" verbs: a script's calls on {@code ctx.player} / {@code ctx.level}
 * / {@code ctx.attacker} reach the {@link PlayerActions} / {@link LevelActions} / {@link EntityActions}
 * bridges with the right arguments, coordinates accept ints or floats, and named defaults hold - all
 * without launching Minecraft.
 */
class WorldActionsTest {

    private static final class RecPlayer implements PlayerActions {
        boolean healed, cleared, killed;
        double health = Double.NaN, damage = Double.NaN;
        String effectId, gamemode, soundId;
        int effectSeconds, effectAmp, xp;
        boolean effectParticles;
        double soundVol, soundPitch;
        @Override public void tell(MineText message) { }
        @Override public void give(String itemId, int count) { }
        @Override public void teleport(double x, double y, double z) { }
        @Override public void heal() { healed = true; }
        @Override public void setHealth(double h) { health = h; }
        @Override public void damage(double amount) { damage = amount; }
        @Override public void effect(String id, int seconds, int amplifier, boolean showParticles) {
            effectId = id; effectSeconds = seconds; effectAmp = amplifier; effectParticles = showParticles;
        }
        @Override public void clearEffects() { cleared = true; }
        @Override public void giveXp(int points) { xp = points; }
        @Override public void setGamemode(String mode) { gamemode = mode; }
        @Override public void playSound(String id, double volume, double pitch) {
            soundId = id; soundVol = volume; soundPitch = pitch;
        }
        @Override public void kill() { killed = true; }
    }

    private static final class RecLevel implements LevelActions {
        String blockId, spawnId, weather, soundId, particleId;
        int bx, by, bz, particleCount;
        double sx, sy, sz, ex, ey, ez, power, lx, ly, lz;
        long time = -1;
        boolean fire, destroy, exploded, struck;
        String getReturns = "minecraft:dirt";
        @Override public void setBlock(int x, int y, int z, String id) { bx = x; by = y; bz = z; blockId = id; }
        @Override public String getBlock(int x, int y, int z) { return getReturns; }
        @Override public void spawn(String id, double x, double y, double z) { spawnId = id; sx = x; sy = y; sz = z; }
        @Override public void playSound(String id, double x, double y, double z, double v, double p) {
            soundId = id; soundVol = v; soundPitch = p;
        }
        double soundVol, soundPitch;
        @Override public void spawnParticle(String id, double x, double y, double z, int count) {
            particleId = id; particleCount = count;
        }
        @Override public void setTime(long ticks) { time = ticks; }
        @Override public void setWeather(String kind) { weather = kind; }
        @Override public void explode(double x, double y, double z, double pw, boolean f, boolean d) {
            ex = x; ey = y; ez = z; power = pw; fire = f; destroy = d; exploded = true;
        }
        @Override public void strikeLightning(double x, double y, double z) { lx = x; ly = y; lz = z; struck = true; }
        @Override public java.util.List<EntityView> entitiesNear(double x, double y, double z, double r, String t) { return java.util.List.of(); }
        @Override public java.util.List<PlayerView> players() { return java.util.List.of(); }
        @Override public PlayerView nearestPlayer(double x, double y, double z) { return null; }
    }

    private static final class RecEntity implements EntityActions {
        boolean killed;
        String effectId;
        int seconds, amp;
        double tx, ty, tz, damage = Double.NaN;
        @Override public void kill() { killed = true; }
        @Override public void effect(String id, int s, int a, boolean particles) { effectId = id; seconds = s; amp = a; }
        @Override public void teleport(double x, double y, double z) { tx = x; ty = y; tz = z; }
        @Override public void damage(double amount) { damage = amount; }
    }

    private static Events run(Path dir, TestLog log, String script) throws IOException {
        Files.writeString(dir.resolve("w.star"), script);
        return StarlarkHost.runServer(dir, log).events();
    }

    private static PlayerView player(PlayerActions actions, LevelView level) {
        return new PlayerView("Steve", "u", 1.0, 64.0, 2.0, 20.0, ItemStackView.empty(), level, actions);
    }

    @Test
    void playerVerbsReachActions(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    ctx.player.heal()
                    ctx.player.set_health(7)
                    ctx.player.damage(3.5)
                    ctx.player.effect("speed", seconds = 10, amplifier = 1, show_particles = False)
                    ctx.player.clear_effects()
                    ctx.player.give_xp(42)
                    ctx.player.set_gamemode("creative")
                    ctx.player.play_sound("minecraft:entity.player.levelup", volume = 0.5, pitch = 2)
                    ctx.player.kill()
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        RecPlayer a = new RecPlayer();
        events.fire("minelark:player_joined", new EventContext("minelark:player_joined",
                Map.of("player", player(a, new LevelView("minecraft:overworld", 0, true, false))),
                Set.of(), false), log);

        assertFalse(log.anyMessageContains("error"), "got " + log.messages);
        assertTrue(a.healed);
        assertEquals(7.0, a.health);
        assertEquals(3.5, a.damage);
        assertEquals("speed", a.effectId);
        assertEquals(10, a.effectSeconds);
        assertEquals(1, a.effectAmp);
        assertFalse(a.effectParticles);
        assertTrue(a.cleared);
        assertEquals(42, a.xp);
        assertEquals("creative", a.gamemode);
        assertEquals("minecraft:entity.player.levelup", a.soundId);
        assertEquals(0.5, a.soundVol);
        assertEquals(2.0, a.soundPitch);
        assertTrue(a.killed);
    }

    @Test
    void effectDefaultsHold(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    ctx.player.effect("regeneration")
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        RecPlayer a = new RecPlayer();
        events.fire("minelark:player_joined", new EventContext("minelark:player_joined",
                Map.of("player", player(a, new LevelView("minecraft:overworld", 0, true, false))),
                Set.of(), false), log);

        assertEquals("regeneration", a.effectId);
        assertEquals(30, a.effectSeconds);
        assertEquals(0, a.effectAmp);
        assertTrue(a.effectParticles);
    }

    @Test
    void levelVerbsReachActions(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    ctx.level.set_block(1, 64, -3, "minecraft:stone")
                    ctx.level.spawn("minecraft:zombie", 1, 2.0, 3)
                    ctx.level.set_time(6000)
                    ctx.level.set_weather("rain")
                    ctx.level.spawn_particle("minecraft:heart", 1, 2, 3, count = 5)
                    ctx.level.explode(1, 2, 3, power = 2.0, fire = True, destroy_blocks = False)
                    ctx.level.strike_lightning(4, 5, 6)
                    log.info("here=" + ctx.level.get_block(0, 0, 0))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        RecLevel lv = new RecLevel();
        lv.getReturns = "minecraft:bedrock";
        LevelView level = new LevelView("minecraft:overworld", 0, true, false, lv);
        events.fire("minelark:player_joined", new EventContext("minelark:player_joined",
                Map.of("player", player(new RecPlayer(), level), "level", level), Set.of(), false), log);

        assertFalse(log.anyMessageContains("error"), "got " + log.messages);
        assertEquals("minecraft:stone", lv.blockId);
        assertEquals(1, lv.bx);
        assertEquals(64, lv.by);
        assertEquals(-3, lv.bz);
        assertEquals("minecraft:zombie", lv.spawnId);
        assertEquals(2.0, lv.sy);   // 2.0 passed as a float
        assertEquals(6000L, lv.time);
        assertEquals("rain", lv.weather);
        assertEquals("minecraft:heart", lv.particleId);
        assertEquals(5, lv.particleCount);
        assertTrue(lv.exploded);
        assertEquals(2.0, lv.power);
        assertTrue(lv.fire);
        assertFalse(lv.destroy);
        assertTrue(lv.struck);
        assertTrue(log.anyMessageContains("here=minecraft:bedrock"));
    }

    @Test
    void entityVerbsReachActions(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_death(ctx):
                    ctx.attacker.effect("slowness", seconds = 5, amplifier = 2)
                    ctx.attacker.damage(4)
                    ctx.attacker.teleport(0, 100, 0)
                    ctx.attacker.kill()
                events.minelark.PLAYER_DEATH.on(on_death)
                """);
        RecEntity ent = new RecEntity();
        EntityView creeper = new EntityView("minecraft:creeper", "uc", "Creeper", 3, 64, 3,
                new LevelView("minecraft:overworld", 0, true, false), ent);
        events.fire("minelark:player_death", new EventContext("minelark:player_death",
                Map.of("player", new PlayerView("Steve", "u", m -> {}), "attacker", creeper), Set.of(), true), log);

        assertFalse(log.anyMessageContains("error"), "got " + log.messages);
        assertEquals("slowness", ent.effectId);
        assertEquals(5, ent.seconds);
        assertEquals(2, ent.amp);
        assertEquals(4.0, ent.damage);
        assertEquals(100.0, ent.ty);
        assertTrue(ent.killed);
    }

    @Test
    void readOnlyViewsNoOpAndDoNotThrow(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    ctx.player.heal()          # convenience ctor => no-op actions
                    ctx.level.set_block(0, 0, 0, "minecraft:stone")   # NOOP level
                    log.info("survived")
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        // The message-only convenience constructor supplies no-op actions and a read-only level.
        events.fire("minelark:player_joined", new EventContext("minelark:player_joined",
                Map.of("player", new PlayerView("Steve", "u", m -> {}),
                        "level", new LevelView("minecraft:overworld", 0, true, false)),
                Set.of(), false), log);

        assertTrue(log.anyMessageContains("survived"), "got " + log.messages);
        assertFalse(log.anyMessageContains("error"));
    }
}
