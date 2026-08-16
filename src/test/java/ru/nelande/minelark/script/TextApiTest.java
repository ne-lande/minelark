package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the {@code text(...)} / {@code translate(...)} builtins and the {@link MineText}
 * component they build. Components are captured through a fake {@code ctx.player.tell} sink.
 */
class TextApiTest {

    private static Events run(Path dir, TestLog log, String script) throws IOException {
        Files.writeString(dir.resolve("t.star"), script);
        return StarlarkHost.runServer(dir, log).events();
    }

    /** Runs {@code script} (which subscribes to PLAYER_JOINED and tells the player) and returns what was told. */
    private static List<MineText> tellAll(Path dir, TestLog log, String script) throws IOException {
        Events events = run(dir, log, script);
        List<MineText> sent = new ArrayList<>();
        PlayerView player = new PlayerView("Steve", "uuid-1", sent::add);
        events.fire("minelark:player_joined",
                new EventContext("minelark:player_joined", Map.of("player", player), Set.of(), false), log);
        return sent;
    }

    @Test
    void styledComponentIsBuilt(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        List<MineText> sent = tellAll(dir, log, """
                def on_join(ctx):
                    ctx.player.tell(
                        text("Hello").color("gold").bold().hover("a tip").click_run("/spawn").append(" world"))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);

        assertEquals(1, sent.size(), "got " + log.messages);
        MineText t = sent.get(0);
        assertEquals("Hello", t.literal());
        assertEquals("gold", t.colorValue());
        assertEquals(Boolean.TRUE, t.boldValue());
        assertNotNull(t.hoverText());
        assertEquals("a tip", t.hoverText().literal());
        assertEquals(MineText.ClickAction.RUN_COMMAND, t.clickAction());
        assertEquals("/spawn", t.clickValue());
        assertEquals(1, t.extra().size());
        assertEquals(" world", t.extra().get(0).literal());
    }

    @Test
    void hexColourIsAccepted(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        List<MineText> sent = tellAll(dir, log, """
                def on_join(ctx):
                    ctx.player.tell(text("hi").color("#ff8800"))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        assertEquals("#ff8800", sent.get(0).colorValue(), "got " + log.messages);
    }

    @Test
    void unknownColourIsReportedNotThrown(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        List<MineText> sent = tellAll(dir, log, """
                def on_join(ctx):
                    ctx.player.tell(text("hi").color("burple"))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        assertTrue(sent.isEmpty(), "the tell should never run");
        assertTrue(log.anyMessageContains("unknown colour"), "got " + log.messages);
    }

    @Test
    void translateWithArgs(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        List<MineText> sent = tellAll(dir, log, """
                def on_join(ctx):
                    ctx.player.tell(translate("chat.type.text", [ctx.player.name, text("hi")]))
                events.minelark.PLAYER_JOINED.on(on_join)
                """);

        MineText t = sent.get(0);
        assertTrue(t.isTranslation(), "got " + log.messages);
        assertEquals("chat.type.text", t.translateKey());
        assertEquals(2, t.translateArgs().size());
        assertEquals("Steve", t.translateArgs().get(0).literal());
        assertEquals("hi", t.translateArgs().get(1).literal());
    }

    @Test
    void plainStringIsCoerced(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        List<MineText> sent = tellAll(dir, log, """
                def on_join(ctx):
                    ctx.player.tell("just text")
                events.minelark.PLAYER_JOINED.on(on_join)
                """);
        assertEquals("just text", sent.get(0).literal(), "got " + log.messages);
        assertNull(sent.get(0).colorValue());
    }

    @Test
    void componentsAreImmutableAcrossFreeze(@TempDir Path dir) throws IOException {
        // BASE is a module-level value (frozen after load); chaining on it in the callback must not
        // mutate it, and must not raise "cannot mutate frozen".
        TestLog log = new TestLog();
        List<MineText> sent = tellAll(dir, log, """
                BASE = text("a")

                def on_join(ctx):
                    styled = BASE.color("red")
                    ctx.player.tell(BASE)
                    ctx.player.tell(styled)

                events.minelark.PLAYER_JOINED.on(on_join)
                """);

        assertEquals(2, sent.size(), "got " + log.messages);
        assertNull(sent.get(0).colorValue(), "BASE must stay uncoloured");
        assertEquals("red", sent.get(1).colorValue());
    }
}
