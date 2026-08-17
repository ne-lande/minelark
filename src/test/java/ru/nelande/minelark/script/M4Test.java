package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1/Tier-2 tests for the M4 server-phase namespaces: tags, loot, and datapack. */
class M4Test {

    private static ServerResult run(Path dir, TestLog log, String script) throws IOException {
        Files.writeString(dir.resolve("s.star"), script);
        return StarlarkHost.runServer(dir, log);
    }

    @Test
    void tagsResolveKindNamespaceAndMembers(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        ServerResult r = run(dir, log, """
                tags.block("ores", ["minelark:ruby_ore", "iron_ore"])
                tags.fluid("minecraft:water", ["minelark:flowing_acid"])
                tags.entity("bosses", ["minecraft:ender_dragon"])
                """);

        assertEquals(3, r.tags().size(), "got " + log.messages);
        TagSpec block = r.tags().get(0);
        assertEquals("block", block.kind());
        assertEquals("c:ores", block.tag());                       // bare tag -> c:
        assertEquals("minelark:ruby_ore", block.members().get(0));
        assertEquals("minecraft:iron_ore", block.members().get(1)); // bare member -> minecraft:
        assertEquals("fluid", r.tags().get(1).kind());
        assertEquals("entity_type", r.tags().get(2).kind());
    }

    @Test
    void entityDropsBuildLootTableJson(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        ServerResult r = run(dir, log, """
                loot.entity_drops("minecraft:zombie", [
                    "minelark:ruby",
                    {"item": "minecraft:emerald", "count": [0, 2], "chance": 0.5},
                ])
                """);

        assertEquals(1, r.entityDrops().size(), "got " + log.messages);
        EntityDropSpec spec = r.entityDrops().get(0);
        assertEquals("minecraft:zombie", spec.entityId());
        String json = spec.json();
        assertTrue(json.contains("\"type\":\"minecraft:entity\""), json);
        assertTrue(json.contains("\"name\":\"minelark:ruby\""), json);
        assertTrue(json.contains("\"function\":\"minecraft:set_count\""), json);
        assertTrue(json.contains("\"min\":0,\"max\":2"), json);
        assertTrue(json.contains("\"condition\":\"minecraft:random_chance\",\"chance\":0.5"), json);
    }

    @Test
    void lootInjectCollectsDrops(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        ServerResult r = run(dir, log, """
                loot.inject("minecraft:chests/simple_dungeon", [{"item": "minelark:ruby", "count": 3}])
                """);

        assertEquals(1, r.lootInjects().size(), "got " + log.messages);
        LootInjectSpec spec = r.lootInjects().get(0);
        assertEquals("minecraft:chests/simple_dungeon", spec.tableId());
        LootDrop drop = spec.drops().get(0);
        assertEquals("minelark:ruby", drop.itemId());
        assertEquals(3, drop.min());
        assertEquals(3, drop.max());
    }

    @Test
    void datapackJsonConvertsStarlarkValues(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        ServerResult r = run(dir, log, """
                datapack.json("minelark/predicate/on_fire", {
                    "condition": "minecraft:entity_properties",
                    "count": 3,
                    "ratio": 1.5,
                    "flags": [True, False, None],
                })
                """);

        assertEquals(1, r.datapackJson().size(), "got " + log.messages);
        DatapackJsonSpec spec = r.datapackJson().get(0);
        assertEquals("data/minelark/predicate/on_fire.json", spec.path());
        String json = spec.json();
        assertTrue(json.contains("\"count\":3"), json);
        assertTrue(json.contains("\"ratio\":1.5"), json);
        assertTrue(json.contains("\"flags\":[true,false,null]"), json);
    }

    @Test
    void datapackJsonRejectsUnsafePath(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        ServerResult r = run(dir, log, """
                datapack.json("../escape", {"x": 1})
                """);

        assertTrue(r.datapackJson().isEmpty());
        assertTrue(log.anyMessageContains("is invalid"), "got " + log.messages);
    }
}
