package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the M7 interop bridge: the {@code mods} and {@code registry} namespaces, available
 * to server and client scripts. Fakes stand in for the mod loader and the game registries so the whole
 * thing runs without Minecraft.
 */
class InteropTest {

    /** A scripted stand-in for the mod loader: a fixed set of "installed" mods. */
    private static final PlatformInfo FAKE_PLATFORM = new PlatformInfo() {
        private final Map<String, String> mods = Map.of(
                "minecraft", "1.21.1",
                "minelark", "0.1.0",
                "create", "0.5.1");

        @Override public boolean isLoaded(String modId) { return mods.containsKey(modId); }
        @Override public String version(String modId) { return mods.get(modId); }
        @Override public String name(String modId) { return isLoaded(modId) ? "Mod " + modId : null; }
        @Override public List<String> ids() { return mods.keySet().stream().sorted().toList(); }
    };

    /** A scripted stand-in for the game registries: a couple of items across two namespaces. */
    private static final RegistryAccess FAKE_REGISTRY = new RegistryAccess() {
        private final List<String> items = List.of("minecraft:diamond", "minecraft:stick", "create:cogwheel");

        @Override
        public boolean has(Kind kind, String id) {
            return kind == Kind.ITEM && items.contains(id);
        }

        @Override
        public List<String> ids(Kind kind, String namespace) {
            if (kind != Kind.ITEM) {
                return List.of();
            }
            return items.stream()
                    .filter(id -> namespace == null || namespace.isEmpty()
                            || id.substring(0, id.indexOf(':')).equals(namespace))
                    .sorted()
                    .toList();
        }
    };

    private static void write(Path dir, String script) throws IOException {
        Files.writeString(dir.resolve("i.star"), script);
    }

    private static final String PROBE = """
            log.info("create=" + str(mods.loaded("create")))
            log.info("absent=" + str(mods.loaded("nope")))
            log.info("ver=" + str(mods.version("create")))
            log.info("nover=" + str(mods.version("nope")))
            log.info("name=" + str(mods.name("create")))
            log.info("list=" + str(mods.list()))
            log.info("hasdia=" + str(registry.item_exists("diamond")))
            log.info("hascreate=" + str(registry.item_exists("create:cogwheel")))
            log.info("hasnope=" + str(registry.item_exists("nope")))
            log.info("noblock=" + str(registry.block_exists("stone")))
            log.info("creates=" + str(registry.items("create")))
            log.info("allitems=" + str(registry.items()))
            """;

    private static void assertProbe(TestLog log) {
        // mods
        assertTrue(log.anyMessageContains("create=True"), "got " + log.messages);
        assertTrue(log.anyMessageContains("absent=False"), "got " + log.messages);
        assertTrue(log.anyMessageContains("ver=0.5.1"), "got " + log.messages);
        assertTrue(log.anyMessageContains("nover=None"), "got " + log.messages);
        assertTrue(log.anyMessageContains("name=Mod create"), "got " + log.messages);
        assertTrue(log.anyMessageContains("list=[\"create\", \"minecraft\", \"minelark\"]"), "got " + log.messages);
        // registry: bare id normalizes to minecraft:
        assertTrue(log.anyMessageContains("hasdia=True"), "got " + log.messages);
        assertTrue(log.anyMessageContains("hascreate=True"), "got " + log.messages);
        assertTrue(log.anyMessageContains("hasnope=False"), "got " + log.messages);
        // block registry is empty in the fake -> stone is not found there
        assertTrue(log.anyMessageContains("noblock=False"), "got " + log.messages);
        // namespace filter vs. everything
        assertTrue(log.anyMessageContains("creates=[\"create:cogwheel\"]"), "got " + log.messages);
        assertTrue(log.anyMessageContains(
                "allitems=[\"create:cogwheel\", \"minecraft:diamond\", \"minecraft:stick\"]"), "got " + log.messages);
    }

    @Test
    void serverScriptsSeeModsAndRegistry(@TempDir Path dir) throws IOException {
        write(dir, PROBE);
        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, FAKE_PLATFORM, FAKE_REGISTRY, log);
        assertProbe(log);
    }

    @Test
    void clientScriptsSeeModsAndRegistry(@TempDir Path dir) throws IOException {
        write(dir, PROBE);
        TestLog log = new TestLog();
        StarlarkHost.runClient(dir, noopClient(), FAKE_PLATFORM, FAKE_REGISTRY, log);
        assertProbe(log);
    }

    @Test
    void startupScriptsDoNotSeeTheInteropNamespaces(@TempDir Path dir) throws IOException {
        // The bridge is deliberately server+client only (startup runs before the registries freeze).
        write(dir, """
                mods.loaded("create")
                """);
        TestLog log = new TestLog();
        StarlarkHost.runStartup(dir, log);
        assertTrue(log.anyMessageContains("'mods'"), "expected an undefined-name error, got " + log.messages);
    }

    @Test
    void namespaceFilterDefaultsToAllWhenOmitted(@TempDir Path dir) throws IOException {
        write(dir, """
                log.info("count=" + str(len(registry.items())))
                """);
        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, FAKE_PLATFORM, FAKE_REGISTRY, log);
        assertTrue(log.anyMessageContains("count=3"), "got " + log.messages);
    }

    private static ClientAccess noopClient() {
        return new ClientAccess() {
            @Override public PlayerView player() { return null; }
            @Override public LevelView world() { return null; }
            @Override public void sendChat(String message) { }
            @Override public void showMessage(MineText message) { }
        };
    }
}
