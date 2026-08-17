package ru.nelande.minelark.script;

import net.starlark.java.eval.EvalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1 tests for the {@code net} namespace (channel dispatch + outbound sends; the packet is adapter-side). */
class NetworkTest {

    /** A server sender that records what scripts asked to send. */
    private static final class RecordingServer implements ServerNetwork {
        final List<String> unicast = new ArrayList<>();   // "uuid|channel|json"
        final List<String> broadcast = new ArrayList<>(); // "channel|json"

        @Override
        public void sendToPlayer(String uuid, String channel, String json) {
            unicast.add(uuid + "|" + channel + "|" + json);
        }

        @Override
        public void broadcast(String channel, String json) {
            broadcast.add(channel + "|" + json);
        }
    }

    @Test
    void serverSendAndBroadcastReachTheSender(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("s.star"), """
                net.send("11111111-2222-3333-4444-555555555555", "hello", {"msg": "hi", "n": 3})
                net.broadcast("news", [1, 2, 3])
                """);

        RecordingServer sender = new RecordingServer();
        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, PlatformInfo.EMPTY, RegistryAccess.EMPTY,
                new Storage(null), new Storage(null), sender, log);

        assertEquals(List.of("11111111-2222-3333-4444-555555555555|hello|{\"msg\":\"hi\",\"n\":3}"),
                sender.unicast);
        assertEquals(List.of("news|[1,2,3]"), sender.broadcast);
    }

    @Test
    void serverOnReceivesChannelDataAndPlayer(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("s.star"), """
                def handler(ctx):
                    log.info("got ch=" + ctx.channel + " who=" + ctx.player.name + " x=" + str(ctx.data["x"]))
                net.on("moves", handler)
                """);

        TestLog log = new TestLog();
        ServerResult result = StarlarkHost.runServer(dir, PlatformInfo.EMPTY, RegistryAccess.EMPTY,
                new Storage(null), new Storage(null), ServerNetwork.NOOP, log);

        PlayerView player = new PlayerView("Steve", "uuid-1", message -> { });
        result.network().dispatch("moves", "{\"x\": 42}", player, log);

        assertTrue(log.anyMessageContains("got ch=moves who=Steve x=42"), "got " + log.messages);
    }

    @Test
    void clientSendAndReceive(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("c.star"), """
                net.send("ping", {"v": 1})
                def handler(ctx):
                    log.info("client got " + ctx.channel + " val=" + str(ctx.data["v"]))
                net.on("pong", handler)
                """);

        List<String> sent = new ArrayList<>();
        ClientNetwork sender = (channel, json) -> sent.add(channel + "|" + json);
        TestLog log = new TestLog();
        ClientResult result = StarlarkHost.runClient(dir, NOOP_CLIENT,
                PlatformInfo.EMPTY, RegistryAccess.EMPTY, sender, log);

        assertEquals(List.of("ping|{\"v\":1}"), sent);

        result.network().dispatch("pong", "{\"v\": 7}", log);
        assertTrue(log.anyMessageContains("client got pong val=7"), "got " + log.messages);
    }

    @Test
    void rejectsBadChannel() {
        ServerNetworkApi net = new ServerNetworkApi(ServerNetwork.NOOP, new Log(new TestLog()));
        assertThrows(EvalException.class, () -> net.broadcast("bad channel", "x"));
    }

    /** A client with no world - enough for scripts that only touch the {@code net} namespace. */
    private static final ClientAccess NOOP_CLIENT = new ClientAccess() {
        @Override
        public PlayerView player() {
            return null;
        }

        @Override
        public LevelView world() {
            return null;
        }

        @Override
        public void sendChat(String message) {
        }

        @Override
        public void showMessage(MineText message) {
        }
    };
}
