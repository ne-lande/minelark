package ru.nelande.minelark.script;

import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1 tests for the persistent {@code storage} key-value store. */
class StorageTest {

    @Test
    void setGetHasDeleteClear() throws EvalException {
        Storage s = new Storage(null);
        s.set("name", "Steve");
        s.set("count", StarlarkInt.of(5));

        assertEquals("Steve", s.get("name", Starlark.NONE));
        assertEquals(StarlarkInt.of(5), s.get("count", Starlark.NONE));
        assertTrue(s.has("name"));
        assertEquals(List.of("name", "count"), s.storedKeys());

        assertTrue(s.delete("name"));
        assertFalse(s.delete("name"));
        assertFalse(s.has("name"));

        s.clear();
        assertEquals(List.of(), s.storedKeys());
    }

    @Test
    void getReturnsDefaultWhenAbsent() {
        Storage s = new Storage(null);
        assertEquals(Starlark.NONE, s.get("missing", Starlark.NONE));
        assertEquals("fallback", s.get("missing", "fallback"));
    }

    @Test
    void persistsAcrossInstances(@TempDir Path dir) throws EvalException {
        Path file = dir.resolve("storage.json");
        Storage a = new Storage(file);
        a.set("greeting", "hello");
        a.set("n", StarlarkInt.of(42));

        // A fresh store over the same file (as after a reload/restart) sees the data.
        Storage b = new Storage(file);
        assertEquals("hello", b.get("greeting", Starlark.NONE));
        assertEquals(StarlarkInt.of(42), b.get("n", Starlark.NONE));
    }

    @Test
    void nestedValuesRoundtrip(@TempDir Path dir) throws EvalException {
        Path file = dir.resolve("storage.json");
        Object nested = StarlarkJson.fromJsonString("{\"players\":[\"a\",\"b\"],\"meta\":{\"score\":3}}");
        Storage a = new Storage(file);
        a.set("data", nested);

        Storage b = new Storage(file);
        assertEquals("{\"players\":[\"a\",\"b\"],\"meta\":{\"score\":3}}",
                StarlarkJson.toJsonString(b.get("data", Starlark.NONE)));
    }

    @Test
    void namespaceIsUsableFromServerScripts(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("s.star"), """
                storage.set("greeting", "hi")
                log.info("got=" + storage.get("greeting") + " has=" + str(storage.has("greeting")))
                """);

        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, log);

        assertTrue(log.anyMessageContains("got=hi has=True"), "got " + log.messages);
    }

    @Test
    void perPlayerStoresAreIsolatedCachedAndPersist(@TempDir Path dir) throws EvalException {
        Storage global = new Storage(null);
        Path players = dir.resolve("players");
        global.bindPlayerDir(players);

        global.player("aaaa").set("coins", StarlarkInt.of(5));
        global.player("bbbb").set("coins", StarlarkInt.of(9));

        // Each player has their own bag...
        assertEquals(StarlarkInt.of(5), global.player("aaaa").get("coins", Starlark.NONE));
        assertEquals(StarlarkInt.of(9), global.player("bbbb").get("coins", Starlark.NONE));
        // ...and repeated lookups return the same live object (so writes aren't lost).
        assertSame(global.player("aaaa"), global.player("aaaa"));
        // ...written to a per-uuid file that a fresh store sees (persists across restarts).
        Storage reloaded = new Storage(players.resolve("aaaa.json"));
        assertEquals(StarlarkInt.of(5), reloaded.get("coins", Starlark.NONE));
    }

    @Test
    void playerRequiresBoundDirAndRejectsUnsafeId(@TempDir Path dir) {
        Storage unbound = new Storage(null);
        assertThrows(EvalException.class, () -> unbound.player("aaaa"));

        Storage bound = new Storage(null);
        bound.bindPlayerDir(dir);
        assertThrows(EvalException.class, () -> bound.player("../evil"));
    }

    @Test
    void bindFileRebindsAndUnbindDetaches(@TempDir Path dir) throws EvalException {
        Path worldA = dir.resolve("a/world.json");
        Path worldB = dir.resolve("b/world.json");
        Storage world = new Storage(null);

        world.bindFile(worldA);
        world.set("k", StarlarkInt.of(1));
        // Rebinding to a different (empty) world file starts clean, not carrying A's data.
        world.bindFile(worldB);
        assertFalse(world.has("k"));
        world.set("k", StarlarkInt.of(2));

        // Each file kept its own value.
        assertEquals(StarlarkInt.of(1), new Storage(worldA).get("k", Starlark.NONE));
        assertEquals(StarlarkInt.of(2), new Storage(worldB).get("k", Starlark.NONE));

        // Unbinding on server stop drops the live data and disables per-player access.
        world.unbindWorld();
        assertEquals(List.of(), world.storedKeys());
        assertThrows(EvalException.class, () -> world.player("aaaa"));
    }

    @Test
    void worldAndPlayerNamespacesUsableFromServerScripts(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("s.star"), """
                world.set("spawned", True)
                p = storage.player("11111111-2222-3333-4444-555555555555")
                p.set("coins", 10)
                log.info("world=" + str(world.get("spawned")) + " coins=" + str(p.get("coins")))
                """);

        Storage global = new Storage(null);
        global.bindPlayerDir(dir.resolve("players"));
        Storage world = new Storage(dir.resolve("world.json"));
        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, PlatformInfo.EMPTY, RegistryAccess.EMPTY, global, world, log);

        assertTrue(log.anyMessageContains("world=True coins=10"), "got " + log.messages);
    }
}
