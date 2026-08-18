package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the world-query ({@code ctx.level.entities_near/players/nearest_player}) and
 * inventory ({@code ctx.player.count/has/remove}) surfaces: scripts reach the bridges and read back
 * what they return, without a running game. The real MC queries are proven live.
 */
class QueryInventoryTest {

    /** A {@link PlayerActions} with a fake inventory the script can query and deplete. */
    private static final class InventoryActions implements PlayerActions {
        final Map<String, Integer> stock = new HashMap<>();
        @Override public void tell(MineText message) { }
        @Override public void give(String itemId, int count) { }
        @Override public void teleport(double x, double y, double z) { }
        @Override public int count(String itemId) { return stock.getOrDefault(itemId, 0); }
        @Override public boolean has(String itemId, int count) { return count(itemId) >= count; }
        @Override public int remove(String itemId, int count) {
            int removed = Math.min(count, count(itemId));
            stock.put(itemId, count(itemId) - removed);
            return removed;
        }
    }

    /** A {@link LevelActions} whose queries return canned lists; everything else is a no-op. */
    private static final class QueryLevel implements LevelActions {
        List<EntityView> near = List.of();
        List<PlayerView> players = List.of();
        PlayerView nearest;
        @Override public void setBlock(int x, int y, int z, String b) { }
        @Override public String getBlock(int x, int y, int z) { return "minecraft:air"; }
        @Override public void spawn(String e, double x, double y, double z) { }
        @Override public void playSound(String s, double x, double y, double z, double v, double p) { }
        @Override public void spawnParticle(String s, double x, double y, double z, int c) { }
        @Override public void setTime(long t) { }
        @Override public void setWeather(String k) { }
        @Override public void explode(double x, double y, double z, double pw, boolean f, boolean d) { }
        @Override public void strikeLightning(double x, double y, double z) { }
        @Override public List<EntityView> entitiesNear(double x, double y, double z, double r, String type) { return near; }
        @Override public List<PlayerView> players() { return players; }
        @Override public PlayerView nearestPlayer(double x, double y, double z) { return nearest; }
    }

    private static Events server(Path dir, TestLog log, String script) throws IOException {
        Files.writeString(dir.resolve("q.star"), script);
        return StarlarkHost.runServer(dir, log).events();
    }

    private static EntityView entity(String type) {
        return new EntityView(type, "u-" + type, type, 0, 0, 0, new LevelView("minecraft:overworld", 0, true, false));
    }

    @Test
    void inventoryCountHasRemove(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_join(ctx):
                    p = ctx.player
                    log.info("count=" + str(p.count("minecraft:emerald")) + " has5=" + str(p.has("minecraft:emerald", count = 5)))
                    took = p.remove("minecraft:emerald", 3)
                    log.info("took=" + str(took) + " left=" + str(p.count("minecraft:emerald")))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        InventoryActions inv = new InventoryActions();
        inv.stock.put("minecraft:emerald", 7);
        PlayerView player = new PlayerView("Steve", "u", 0, 0, 0, 20,
                ItemStackView.empty(), new LevelView("minecraft:overworld", 0, true, false), inv);
        events.fire("minelark:player_joined",
                new EventContext("minelark:player_joined", Map.of("player", player), Set.of(), false), log);

        assertFalse(log.anyMessageContains("error"), "got " + log.messages);
        assertTrue(log.anyMessageContains("count=7 has5=True"), "got " + log.messages);
        assertTrue(log.anyMessageContains("took=3 left=4"), "got " + log.messages);
        assertEquals(4, inv.stock.get("minecraft:emerald"));
    }

    @Test
    void entitiesNearIterates(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_join(ctx):
                    hits = ctx.level.entities_near(ctx.player.x, ctx.player.y, ctx.player.z, radius = 8)
                    log.info("found " + str(len(hits)) + ": " + ", ".join([e.type for e in hits]))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        QueryLevel query = new QueryLevel();
        query.near = List.of(entity("minecraft:zombie"), entity("minecraft:cow"));
        LevelView level = new LevelView("minecraft:overworld", 0, true, false, query);
        PlayerView player = new PlayerView("Steve", "u", m -> {});
        events.fire("minelark:player_joined",
                new EventContext("minelark:player_joined", Map.of("player", player, "level", level), Set.of(), false), log);

        assertTrue(log.anyMessageContains("found 2: minecraft:zombie, minecraft:cow"), "got " + log.messages);
    }

    @Test
    void playersAndNearest(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_join(ctx):
                    ps = ctx.level.players()
                    near = ctx.level.nearest_player(0, 0, 0)
                    log.info("players=" + str(len(ps)) + " nearest=" + (near.name if near != None else "none"))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        QueryLevel query = new QueryLevel();
        PlayerView alice = new PlayerView("Alice", "a", m -> {});
        query.players = List.of(alice, new PlayerView("Bob", "b", m -> {}));
        query.nearest = alice;
        LevelView level = new LevelView("minecraft:overworld", 0, true, false, query);
        events.fire("minelark:player_joined",
                new EventContext("minelark:player_joined",
                        Map.of("player", new PlayerView("Steve", "u", m -> {}), "level", level), Set.of(), false), log);

        assertTrue(log.anyMessageContains("players=2 nearest=Alice"), "got " + log.messages);
    }

    @Test
    void nearestPlayerNoneWhenEmpty(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_join(ctx):
                    near = ctx.level.nearest_player(0, 0, 0)
                    log.info("nearest=" + ("none" if near == None else near.name))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        LevelView level = new LevelView("minecraft:overworld", 0, true, false, new QueryLevel());
        events.fire("minelark:player_joined",
                new EventContext("minelark:player_joined",
                        Map.of("player", new PlayerView("Steve", "u", m -> {}), "level", level), Set.of(), false), log);

        assertTrue(log.anyMessageContains("nearest=none"), "got " + log.messages);
    }
}
