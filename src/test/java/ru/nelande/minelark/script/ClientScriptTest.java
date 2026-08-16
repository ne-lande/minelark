package ru.nelande.minelark.script;

import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the client phase: client scripts get the {@code events}, {@code client}, and
 * {@code debug} namespaces plus the {@code text}/{@code translate} builtins, and the client event
 * constants resolve and fire - all without launching the Minecraft client (which the headless dev
 * env can't run). A {@link FakeClient} stands in for the game-side {@link ClientAccess}.
 */
class ClientScriptTest {

    /** A scripted stand-in for the live client: fixed player/world, and a chat/message log. */
    private static final class FakeClient implements ClientAccess {
        PlayerView player;
        LevelView world = new LevelView("minecraft:overworld", 6000, true, false);
        final List<String> sentChat = new ArrayList<>();
        final List<MineText> shownMessages = new ArrayList<>();

        @Override public PlayerView player() { return player; }
        @Override public LevelView world() { return world; }
        @Override public void sendChat(String message) { sentChat.add(message); }
        @Override public void showMessage(MineText message) { shownMessages.add(message); }
    }

    private static ClientResult runFull(Path dir, ClientAccess access, TestLog log, String script)
            throws IOException {
        Files.writeString(dir.resolve("c.star"), script);
        return StarlarkHost.runClient(dir, access, log);
    }

    private static Events run(Path dir, TestLog log, String script) throws IOException {
        return runFull(dir, new FakeClient(), log, script).events();
    }

    @Test
    void lifecycleAndTickCallbacksFire(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_start(ctx):
                    log.info("client up: " + ctx.event)
                def on_tick(ctx):
                    log.info("tick")
                events.minelark.CLIENT_STARTED.on(on_start)
                events.minelark.CLIENT_TICK.on(on_tick)
                """);

        events.fire("minelark:client_started", log);
        events.fire("minelark:client_tick", log);

        assertTrue(log.anyMessageContains("client up: minelark:client_started"), "got " + log.messages);
        assertTrue(log.anyMessageContains("tick"), "got " + log.messages);
    }

    @Test
    void textBuiltinIsAvailableToClientScripts(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        run(dir, log, """
                greeting = text("hi").color("gold")
                log.info("built " + str(greeting))
                """);

        assertTrue(log.anyMessageContains("built"), "got " + log.messages);
    }

    @Test
    void tooltipCallbackAppendsLines(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_tooltip(ctx):
                    ctx.lines = ctx.lines + ["Owner: " + ctx.item.id]
                events.minelark.ITEM_TOOLTIP.on(on_tooltip)
                """);

        ItemStackView diamond = new ItemStackView("minecraft:diamond", 3, "Diamond", false);
        EventContext ctx = new EventContext("minelark:item_tooltip",
                Map.of("item", diamond, "lines", StarlarkList.immutableCopyOf(List.of())),
                Set.of("lines"), false);
        events.fire("minelark:item_tooltip", ctx, log);

        Object lines = ctx.field("lines");
        assertTrue(lines instanceof Sequence, "lines should be a sequence, got " + lines);
        assertEquals("Owner: minecraft:diamond", ((Sequence<?>) lines).get(0));
    }

    @Test
    void chatReceivedIsCancellable(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def on_chat(ctx):
                    if "spoiler" in ctx.message:
                        ctx.cancel()
                events.minelark.CLIENT_CHAT_RECEIVED.on(on_chat)
                """);

        EventContext hidden = new EventContext("minelark:client_chat_received",
                Map.of("message", "big spoiler here"), Set.of(), true);
        events.fire("minelark:client_chat_received", hidden, log);
        assertTrue(hidden.isCancelled());

        EventContext shown = new EventContext("minelark:client_chat_received",
                Map.of("message", "hello world"), Set.of(), true);
        events.fire("minelark:client_chat_received", shown, log);
        assertFalse(shown.isCancelled());
    }

    @Test
    void clientNamespaceReadsPlayerAndSendsChat(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        FakeClient fake = new FakeClient();
        Events events = runFull(dir, fake, log, """
                def on_tick(ctx):
                    if client.player and client.player.y < 0:
                        client.send_chat("/say falling")
                        client.show_message(text("watch out").color("red"))
                events.minelark.CLIENT_TICK.on(on_tick)
                """).events();

        // No player yet -> callback reads None and does nothing.
        events.fire("minelark:client_tick", log);
        assertTrue(fake.sentChat.isEmpty());

        // Player below the void -> callback sends chat and shows a local message.
        fake.player = new PlayerView("Steve", "uuid-1", 0, -5, 0, 20,
                ItemStackView.empty(), fake.world, noopActions());
        events.fire("minelark:client_tick", log);
        assertEquals(List.of("/say falling"), fake.sentChat);
        assertEquals("watch out", fake.shownMessages.get(0).literal());
    }

    @Test
    void debugNamespaceCollectsLines(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        ClientResult result = runFull(dir, new FakeClient(), log, """
                debug.set("mode", "creative")
                debug.set("home", "not set")
                debug.set("home", "0, 64, 0")
                debug.remove("mode")
                """);

        assertEquals(List.of("home: 0, 64, 0"), result.debug().lines());
    }

    private static PlayerActions noopActions() {
        return new PlayerActions() {
            @Override public void tell(MineText message) { }
            @Override public void give(String itemId, int count) { }
            @Override public void teleport(double x, double y, double z) { }
        };
    }

    @Test
    void serverEventFromClientScriptIsRejected(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def f(ctx):
                    pass
                events.minelark.PLAYER_JOINED.on(f)
                """);

        // The wrong-phase reference errors out, so no listener is registered.
        assertFalse(events.hasListeners("minelark:player_joined"));
        assertTrue(log.anyMessageContains("PLAYER_JOINED is a server event"), "got " + log.messages);
    }

    @Test
    void allClientConstantsResolveAndSubscribe(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Events events = run(dir, log, """
                def f(ctx):
                    pass

                def subscribe_all():
                    for e in [
                        events.minelark.CLIENT_STARTED,
                        events.minelark.CLIENT_STOPPING,
                        events.minelark.CLIENT_TICK,
                        events.minelark.ITEM_TOOLTIP,
                        events.minelark.CLIENT_CHAT_RECEIVED,
                        events.minelark.CLIENT_CHAT_SENT,
                    ]:
                        e.on(f)

                subscribe_all()
                log.info("subscribed all client events")
                """);

        assertTrue(log.anyMessageContains("subscribed all client events"), "got " + log.messages);
        assertTrue(events.hasListeners("minelark:item_tooltip"));
        assertTrue(events.hasListeners("minelark:client_chat_sent"));
        assertFalse(events.hasListeners("minelark:not_a_real_event"));
    }
}
