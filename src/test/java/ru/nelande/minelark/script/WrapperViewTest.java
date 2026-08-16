package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1 tests for the player / level / item-stack / entity wrappers and the safe player actions. */
class WrapperViewTest {

    /** A {@link PlayerActions} that records what a script asked for. */
    private static final class RecordingActions implements PlayerActions {
        MineText told;
        String givenId;
        int givenCount;
        double tx, ty, tz;
        boolean teleported;

        @Override
        public void tell(MineText message) {
            told = message;
        }

        @Override
        public void give(String itemId, int count) {
            givenId = itemId;
            givenCount = count;
        }

        @Override
        public void teleport(double x, double y, double z) {
            tx = x;
            ty = y;
            tz = z;
            teleported = true;
        }
    }

    private static Events run(Path dir, TestLog log, String script) throws IOException {
        Files.writeString(dir.resolve("w.star"), script);
        return StarlarkHost.runServer(dir, log).events();
    }

    private static void fireJoin(Events events, TestLog log, PlayerView player) {
        events.fire("minelark:player_joined",
                new EventContext("minelark:player_joined", Map.of("player", player), Set.of(), false), log);
    }

    @Test
    void playerFieldsAreReadable(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    p = ctx.player
                    log.info("y=" + str(p.y) + " hp=" + str(p.health)
                             + " tool=" + p.held_item.id + " dim=" + p.level.dimension)
                events.minelark.PLAYER_JOINED.on(on_join)
                """);

        PlayerView player = new PlayerView("Steve", "uuid-1", 1.5, 64.0, -2.5, 15.0,
                new ItemStackView("minecraft:diamond_pickaxe", 1, "Pickaxe", false),
                new LevelView("minecraft:the_nether", 6000, true, false),
                new RecordingActions());
        fireJoin(events, log, player);

        assertTrue(log.anyMessageContains("y=64.0 hp=15.0 tool=minecraft:diamond_pickaxe dim=minecraft:the_nether"),
                "got " + log.messages);
    }

    @Test
    void giveAndTeleportReachActions(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    ctx.player.give("minecraft:diamond", 3)
                    ctx.player.teleport(0, 100, 0)
                events.minelark.PLAYER_JOINED.on(on_join)
                """);

        RecordingActions actions = new RecordingActions();
        PlayerView player = new PlayerView("Steve", "uuid-1", 0, 0, 0, 20,
                ItemStackView.empty(), new LevelView("minecraft:overworld", 0, true, false), actions);
        fireJoin(events, log, player);

        assertEquals("minecraft:diamond", actions.givenId, "got " + log.messages);
        assertEquals(3, actions.givenCount);
        assertTrue(actions.teleported);
        assertEquals(0.0, actions.tx);
        assertEquals(100.0, actions.ty);
        assertEquals(0.0, actions.tz);
    }

    @Test
    void emptyHandReadsEmpty(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    log.info("empty=" + str(ctx.player.held_item.is_empty))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);

        // The convenience constructor defaults to an empty hand.
        fireJoin(events, log, new PlayerView("Steve", "uuid-1", m -> {}));
        assertTrue(log.anyMessageContains("empty=True"), "got " + log.messages);
    }

    @Test
    void attackerEntityViewOnDeath(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_death(ctx):
                    log.info("killed by " + ctx.attacker.type + " named " + ctx.attacker.name
                             + " in " + ctx.attacker.level.dimension)
                events.minelark.PLAYER_DEATH.on(on_death)
                """);

        EntityView creeper = new EntityView("minecraft:creeper", "uuid-c", "Creeper", 3.0, 64.0, 3.0,
                new LevelView("minecraft:overworld", 13000, false, true));
        events.fire("minelark:player_death", new EventContext("minelark:player_death",
                Map.of("player", new PlayerView("Steve", "u", m -> {}), "attacker", creeper),
                Set.of(), true), log);

        assertTrue(log.anyMessageContains("killed by minecraft:creeper named Creeper in minecraft:overworld"),
                "got " + log.messages);
    }
}
