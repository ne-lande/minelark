package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for {@link StarlarkHost#runPushedClient}: a pushed script sees only the granted
 * namespaces. Withheld ones ({@code net}, {@code client.send_chat}) are simply absent, so touching
 * them is a name error - the sandbox, proven without a client.
 */
class PushedClientTest {

    private static final class FakeClient implements ClientAccess {
        final List<String> sentChat = new ArrayList<>();
        final List<MineText> shown = new ArrayList<>();
        @Override public PlayerView player() { return null; }
        @Override public LevelView world() { return null; }
        @Override public void sendChat(String message) { sentChat.add(message); }
        @Override public void showMessage(MineText message) { shown.add(message); }
    }

    private static ClientResult run(Path dir, Set<Capability> granted, TestLog log, String script)
            throws IOException {
        Files.writeString(dir.resolve("p.star"), script);
        return StarlarkHost.runPushedClient(
                dir, new FakeClient(), PlatformInfo.EMPTY, RegistryAccess.EMPTY,
                ClientNetwork.NOOP, granted, log);
    }

    @Test
    void grantedHudRunsAndAlwaysOnNamespacesWork(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        ClientResult result = run(dir, Set.of(Capability.HUD), log, """
                # a purely visual pushed script
                text("hi").color("gold")
                hud.text("greeting", "hello", x = 5, y = 5)
                def on_tick(ctx):
                    hud.text("greeting", "tick", x = 5, y = 5)
                events.minelark.CLIENT_TICK.on(on_tick)
                """);
        assertFalse(log.anyMessageContains("error"), "clean run, got " + log.messages);
        assertFalse(result.hud().elements().isEmpty(), "hud element was registered");
        assertTrue(result.events().hasListeners("minelark:client_tick"));
    }

    @Test
    void netIsAbsentWhenNotGranted(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        run(dir, Set.of(Capability.HUD), log, "net.on('data', None)\n");
        assertTrue(log.anyMessageContains("net"), "referencing net must fail; got " + log.messages);
        assertEquals(ScriptLog.Level.ERROR, log.levelOfMessageContaining("net"));
    }

    @Test
    void netWorksWhenGranted(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        ClientResult result = run(dir, Set.of(Capability.NET), log, """
                def on_data(ctx):
                    log.info("got")
                net.on("data", on_data)
                """);
        assertFalse(log.anyMessageContains("error"), "clean run, got " + log.messages);
        assertTrue(result.network().hasListeners("data"));
    }

    @Test
    void sendChatIsAbsentWithClientReadButNotChat(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        run(dir, Set.of(Capability.CLIENT_READ), log, """
                _ = client.player   # reading is allowed
                client.send_chat("/op me")   # sending is not
                """);
        assertTrue(log.anyMessageContains("send_chat"),
                "send_chat must be absent for pushed scripts without CHAT; got " + log.messages);
    }

    @Test
    void clientNamespaceAbsentWithoutClientRead(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        run(dir, Set.of(Capability.HUD), log, "_ = client.world\n");
        assertTrue(log.anyMessageContains("client"), "client namespace must be absent; got " + log.messages);
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
