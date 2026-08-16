package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the mutable event {@code ctx}: data-field reads, cancellation, editable fields,
 * the player wrapper, and the new event constants - all without launching Minecraft.
 */
class EventContextTest {

    private static Events run(Path dir, TestLog log, String script) throws IOException {
        Files.writeString(dir.resolve("e.star"), script);
        return StarlarkHost.runServer(dir, log).events();
    }

    @Test
    void callbackReadsDataFields(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_break(ctx):
                    log.info(ctx.player.name + " broke " + ctx.block + " at y=" + str(ctx.y))
                events.minelark.BLOCK_BROKEN.on(on_break)
                """);

        PlayerView steve = new PlayerView("Steve", "uuid-1", m -> {});
        EventContext ctx = new EventContext("minelark:block_broken",
                Map.of("player", steve, "block", "minecraft:stone", "x", 1, "y", 64, "z", -3),
                Set.of(), true);
        events.fire("minelark:block_broken", ctx, log);

        assertTrue(log.anyMessageContains("Steve broke minecraft:stone at y=64"), "got " + log.messages);
    }

    @Test
    void cancelStopsAndIsReadBack(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_chat(ctx):
                    log.info("cancelled? " + str(ctx.cancelled))
                    ctx.cancel()
                events.minelark.PLAYER_CHAT.on(on_chat)
                """);

        EventContext ctx = new EventContext("minelark:player_chat",
                Map.of("message", "hi"), Set.of("message"), true);
        events.fire("minelark:player_chat", ctx, log);

        assertTrue(ctx.isCancelled(), "adapter should read cancellation back");
        assertTrue(log.anyMessageContains("cancelled? False"), "got " + log.messages);
    }

    @Test
    void cancelledFieldCanBeAssigned(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_chat(ctx):
                    ctx.cancelled = True
                events.minelark.PLAYER_CHAT.on(on_chat)
                """);

        EventContext ctx = new EventContext("minelark:player_chat", Map.of(), Set.of(), true);
        events.fire("minelark:player_chat", ctx, log);

        assertTrue(ctx.isCancelled());
    }

    @Test
    void cancelOnNonCancellableIsReportedNotThrown(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    ctx.cancel()
                events.minelark.PLAYER_JOINED.on(on_join)
                """);

        EventContext ctx = new EventContext("minelark:player_joined", Map.of(), Set.of(), false);
        events.fire("minelark:player_joined", ctx, log);

        assertFalse(ctx.isCancelled());
        assertTrue(log.anyMessageContains("not cancellable"), "got " + log.messages);
    }

    @Test
    void editableFieldIsRewritten(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_chat(ctx):
                    ctx.message = ctx.message.upper()
                events.minelark.PLAYER_CHAT.on(on_chat)
                """);

        EventContext ctx = new EventContext("minelark:player_chat",
                Map.of("message", "hello"), Set.of("message"), true);
        events.fire("minelark:player_chat", ctx, log);

        assertEquals("HELLO", ctx.editedString("message"), "got " + log.messages);
    }

    @Test
    void writingReadOnlyFieldIsReportedNotThrown(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_break(ctx):
                    ctx.block = "minecraft:air"
                events.minelark.BLOCK_BROKEN.on(on_break)
                """);

        EventContext ctx = new EventContext("minelark:block_broken",
                Map.of("block", "minecraft:stone"), Set.of(), true);
        events.fire("minelark:block_broken", ctx, log);

        assertEquals("minecraft:stone", ctx.field("block"), "read-only field must not change");
        assertTrue(log.anyMessageContains("read-only"), "got " + log.messages);
    }

    @Test
    void playerTellReachesSink(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    ctx.player.tell("Welcome, " + ctx.player.name)
                events.minelark.PLAYER_JOINED.on(on_join)
                """);

        AtomicReference<MineText> told = new AtomicReference<>();
        PlayerView alex = new PlayerView("Alex", "uuid-2", told::set);
        EventContext ctx = new EventContext("minelark:player_joined", Map.of("player", alex), Set.of(), false);
        events.fire("minelark:player_joined", ctx, log);

        assertEquals("Welcome, Alex", told.get().literal(), "got " + log.messages);
    }

    @Test
    void unknownFieldIsReportedNotThrown(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_join(ctx):
                    log.info(ctx.nope)
                events.minelark.PLAYER_JOINED.on(on_join)
                """);

        EventContext ctx = new EventContext("minelark:player_joined", Map.of(), Set.of(), false);
        events.fire("minelark:player_joined", ctx, log);

        assertTrue(log.anyMessageContains("no field 'nope'"), "got " + log.messages);
    }

    @Test
    void allNewConstantsResolveAndSubscribe(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def f(ctx):
                    pass

                def subscribe_all():
                    for e in [
                        events.minelark.SERVER_TICK,
                        events.minelark.PLAYER_JOINED,
                        events.minelark.PLAYER_LEFT,
                        events.minelark.PLAYER_DEATH,
                        events.minelark.PLAYER_CHAT,
                        events.minelark.BLOCK_BROKEN,
                        events.minelark.BLOCK_PLACED,
                        events.minelark.COMMAND,
                        events.minelark.EXPLOSION,
                    ]:
                        e.on(f)

                subscribe_all()
                log.info("subscribed all")
                """);

        assertTrue(log.anyMessageContains("subscribed all"), "got " + log.messages);
        assertTrue(events.hasListeners("minelark:command"));
        assertTrue(events.hasListeners("minelark:explosion"));
        assertFalse(events.hasListeners("minelark:not_a_real_event"));
    }
}
