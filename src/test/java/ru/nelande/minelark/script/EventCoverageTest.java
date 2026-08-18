package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the wider event coverage (interaction / combat / lifecycle): each new event's
 * constant resolves in a {@code server/} script, a fired {@code ctx} carries the documented fields,
 * the cancellable ones honour {@code ctx.cancel()}, and referencing one from a {@code client/} script
 * is rejected (scope enforcement). The adapter's Fabric wiring is proven live; here we drive the
 * engine directly, no game needed.
 */
class EventCoverageTest {

    private static Events server(Path dir, TestLog log, String script) throws IOException {
        Files.writeString(dir.resolve("e.star"), script);
        return StarlarkHost.runServer(dir, log).events();
    }

    private static PlayerView player() {
        return new PlayerView("Steve", "u", m -> {});
    }

    private static LevelView level(String dim) {
        return new LevelView(dim, 0, true, false);
    }

    private static EntityView entity(String type) {
        return new EntityView(type, "ue", type, 1, 2, 3, level("minecraft:overworld"));
    }

    private static EventContext fire(Events events, TestLog log, String id, Map<String, Object> data,
            boolean cancellable) {
        EventContext ctx = new EventContext(id, data, Set.of(), cancellable);
        events.fire(id, ctx, log);
        return ctx;
    }

    @Test
    void useBlockCarriesBlockCoordsHandAndCancels(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_use(ctx):
                    log.info("use " + ctx.block + " at " + str(ctx.x) + "," + str(ctx.y) + "," + str(ctx.z)
                             + " with " + ctx.hand + " by " + ctx.player.name)
                    if ctx.block == "minecraft:tnt":
                        ctx.cancel()
                events.minelark.USE_BLOCK.on(on_use)
                """);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("player", player());
        data.put("block", "minecraft:tnt");
        data.put("x", 10);
        data.put("y", 64);
        data.put("z", -3);
        data.put("hand", "main");
        EventContext ctx = fire(events, log, "minelark:use_block", data, true);

        assertTrue(log.anyMessageContains("use minecraft:tnt at 10,64,-3 with main by Steve"), "got " + log.messages);
        assertTrue(ctx.isCancelled(), "tnt right-click should be cancelled");
    }

    @Test
    void useItemCarriesItemAndHand(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_use(ctx):
                    log.info("item " + ctx.item.id + " in " + ctx.hand)
                events.minelark.USE_ITEM.on(on_use)
                """);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("player", player());
        data.put("item", new ItemStackView("minecraft:stick", 1, "Stick", false));
        data.put("hand", "off");
        fire(events, log, "minelark:use_item", data, true);
        assertTrue(log.anyMessageContains("item minecraft:stick in off"), "got " + log.messages);
    }

    @Test
    void attackEntityCarriesTheEntity(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_attack(ctx):
                    log.info("hit " + ctx.entity.type)
                events.minelark.ATTACK_ENTITY.on(on_attack)
                """);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("player", player());
        data.put("entity", entity("minecraft:cow"));
        data.put("hand", "main");
        fire(events, log, "minelark:attack_entity", data, true);
        assertTrue(log.anyMessageContains("hit minecraft:cow"), "got " + log.messages);
    }

    @Test
    void entityDamageReadsAmountAndCancels(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_damage(ctx):
                    log.info("dmg " + str(ctx.amount) + " to " + ctx.entity.type)
                    ctx.cancel()   # invincible mobs
                events.minelark.ENTITY_DAMAGE.on(on_damage)
                """);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entity", entity("minecraft:villager"));
        data.put("source", "mob");
        data.put("amount", 6.0);
        EventContext ctx = fire(events, log, "minelark:entity_damage", data, true);
        assertTrue(log.anyMessageContains("dmg 6.0 to minecraft:villager"), "got " + log.messages);
        assertTrue(ctx.isCancelled());
    }

    @Test
    void dimensionChangeCarriesFromAndTo(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_change(ctx):
                    log.info(ctx.player.name + ": " + ctx.origin.dimension + " -> " + ctx.destination.dimension)
                events.minelark.DIMENSION_CHANGE.on(on_change)
                """);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("player", player());
        data.put("origin", level("minecraft:overworld"));
        data.put("destination", level("minecraft:the_nether"));
        fire(events, log, "minelark:dimension_change", data, false);
        assertTrue(log.anyMessageContains("Steve: minecraft:overworld -> minecraft:the_nether"), "got " + log.messages);
    }

    @Test
    void playerTickReceivesThePlayer(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = server(dir, log, """
                def on_tick(ctx):
                    log.info("tick for " + ctx.player.name)
                events.minelark.PLAYER_TICK.on(on_tick)
                """);
        fire(events, log, "minelark:player_tick", java.util.Map.of("player", player()), false);
        assertTrue(log.anyMessageContains("tick for Steve"), "got " + log.messages);
    }

    @Test
    void serverEventsAreRejectedFromClientScripts(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Files.writeString(dir.resolve("c.star"), """
                def on_use(ctx):
                    log.info("nope")
                events.minelark.USE_BLOCK.on(on_use)
                """);
        StarlarkHost.runClient(dir, new ClientAccess() {
            @Override public PlayerView player() { return null; }
            @Override public LevelView world() { return null; }
            @Override public void sendChat(String message) { }
            @Override public void showMessage(MineText message) { }
        }, log);

        assertTrue(log.anyMessageContains("USE_BLOCK is a server event"), "got " + log.messages);
        assertFalse(log.anyMessageContains("nope"));
    }
}
