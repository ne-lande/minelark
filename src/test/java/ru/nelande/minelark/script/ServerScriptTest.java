package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the server phase's {@code recipes} namespace without Minecraft. */
class ServerScriptTest {

    private static void write(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    private static List<RecipeSpec> run(Path dir, TestLog log) {
        return StarlarkHost.runServer(dir, log).recipes();
    }

    @Test
    void shapedRecipeIsSerialised(@TempDir Path dir) throws IOException {
        write(dir, "r.star", """
                recipes.shaped("minelark:ruby_block", ["RRR", "RRR", "RRR"], {"R": "minelark:ruby"})
                """);

        TestLog log = new TestLog();
        List<RecipeSpec> recipes = run(dir, log);

        assertEquals(1, recipes.size(), "got " + log.messages);
        String json = recipes.get(0).json();
        assertTrue(json.contains("\"type\":\"minecraft:crafting_shaped\""), json);
        assertTrue(json.contains("\"item\":\"minelark:ruby\""), json);
        assertTrue(json.contains("\"id\":\"minelark:ruby_block\""), json);
        assertTrue(json.contains("\"count\":1"), json);
        assertTrue(recipes.get(0).idPath().startsWith("shaped/"), recipes.get(0).idPath());
    }

    @Test
    void shapelessWithTagAndCountAndDefaultNamespace(@TempDir Path dir) throws IOException {
        write(dir, "r.star", """
                recipes.shapeless("torch", ["#minecraft:coals", "stick"], count = 4)
                """);

        TestLog log = new TestLog();
        List<RecipeSpec> recipes = run(dir, log);

        assertEquals(1, recipes.size(), "got " + log.messages);
        String json = recipes.get(0).json();
        assertTrue(json.contains("\"type\":\"minecraft:crafting_shapeless\""), json);
        assertTrue(json.contains("\"tag\":\"minecraft:coals\""), json);
        assertTrue(json.contains("\"item\":\"minecraft:stick\""), json);   // bare name -> minecraft:
        assertTrue(json.contains("\"id\":\"minecraft:torch\""), json);
        assertTrue(json.contains("\"count\":4"), json);
    }

    @Test
    void smeltingRecipeIsSerialised(@TempDir Path dir) throws IOException {
        write(dir, "r.star", """
                recipes.smelting("minelark:ruby", "minelark:ruby_ore", experience = 1.0)
                """);

        TestLog log = new TestLog();
        List<RecipeSpec> recipes = run(dir, log);

        assertEquals(1, recipes.size(), "got " + log.messages);
        String json = recipes.get(0).json();
        assertTrue(json.contains("\"type\":\"minecraft:smelting\""), json);
        assertTrue(json.contains("\"cookingtime\":200"), json);
        assertTrue(json.contains("\"experience\":1.0"), json);
        assertTrue(json.contains("\"item\":\"minelark:ruby_ore\""), json);
    }

    @Test
    void stonecuttingRecipeIsSerialised(@TempDir Path dir) throws IOException {
        write(dir, "r.star", """
                recipes.stonecutting("minelark:marble_slab", "minelark:marble", count = 2)
                """);

        List<RecipeSpec> recipes = run(dir, new TestLog());
        String json = recipes.get(0).json();
        assertTrue(json.contains("\"type\":\"minecraft:stonecutting\""), json);
        assertTrue(json.contains("\"item\":\"minelark:marble\""), json);
        assertTrue(json.contains("\"id\":\"minelark:marble_slab\""), json);
        assertTrue(json.contains("\"count\":2"), json);
    }

    @Test
    void campfireRecipeIsSerialised(@TempDir Path dir) throws IOException {
        write(dir, "r.star", """
                recipes.campfire("minelark:cooked_gem", "minelark:raw_gem", experience = 0.35, cooking_time = 600)
                """);

        List<RecipeSpec> recipes = run(dir, new TestLog());
        String json = recipes.get(0).json();
        assertTrue(json.contains("\"type\":\"minecraft:campfire_cooking\""), json);
        assertTrue(json.contains("\"cookingtime\":600"), json);
        assertTrue(json.contains("\"experience\":0.35"), json);
    }

    @Test
    void smithingRecipeIsSerialised(@TempDir Path dir) throws IOException {
        write(dir, "r.star", """
                recipes.smithing("minelark:mythril_sword", "#minelark:templates", "minecraft:diamond_sword", "minelark:mythril")
                """);

        List<RecipeSpec> recipes = run(dir, new TestLog());
        String json = recipes.get(0).json();
        assertTrue(json.contains("\"type\":\"minecraft:smithing_transform\""), json);
        assertTrue(json.contains("\"tag\":\"minelark:templates\""), json);
        assertTrue(json.contains("\"base\":{\"item\":\"minecraft:diamond_sword\"}"), json);
        assertTrue(json.contains("\"addition\":{\"item\":\"minelark:mythril\"}"), json);
        assertTrue(json.contains("\"id\":\"minelark:mythril_sword\""), json);
    }

    @Test
    void removeCollectsFilters(@TempDir Path dir) throws IOException {
        write(dir, "r.star", """
                recipes.remove({"output": "minecraft:torch"})
                recipes.remove({"mod": "minecraft", "type": "minecraft:crafting_shapeless"})
                """);

        TestLog log = new TestLog();
        List<RemovalSpec> removals = StarlarkHost.runServer(dir, log).recipeRemovals();

        assertEquals(2, removals.size(), "got " + log.messages);
        assertEquals("minecraft:torch", removals.get(0).output());
        assertEquals("minecraft", removals.get(1).mod());
        assertEquals("minecraft:crafting_shapeless", removals.get(1).type());
    }

    @Test
    void removeWithNoKeysIsReported(@TempDir Path dir) throws IOException {
        write(dir, "r.star", """
                recipes.remove({})
                """);

        TestLog log = new TestLog();
        List<RemovalSpec> removals = StarlarkHost.runServer(dir, log).recipeRemovals();

        assertTrue(removals.isEmpty());
        assertTrue(log.anyMessageContains("at least one of"), "got " + log.messages);
    }

    @Test
    void eventCallbackFiresWithCtx(@TempDir Path dir) throws IOException {
        write(dir, "e.star", """
                def greet(ctx):
                    log.info("fired: " + ctx.event)

                events.minelark.SERVER_STARTED.on(greet)
                """);

        TestLog log = new TestLog();
        ServerResult result = StarlarkHost.runServer(dir, log);
        result.events().fire("minelark:server_started", log);

        assertTrue(log.anyMessageContains("fired: minelark:server_started"), "got " + log.messages);
    }

    @Test
    void addRemoveAndListWork(@TempDir Path dir) throws IOException {
        write(dir, "e.star", """
                def a(ctx):
                    pass

                EVENT = events.minelark.SERVER_STARTED
                EVENT.add(a)
                EVENT.add(a)
                removed = EVENT.remove(a)
                log.info("removed=" + str(removed) + " remaining=" + str(len(EVENT.list())))
                """);

        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, log);

        assertTrue(log.anyMessageContains("removed=True remaining=1"), "got " + log.messages);
    }

    @Test
    void ofLooksUpByRawId(@TempDir Path dir) throws IOException {
        write(dir, "e.star", """
                def greet(ctx):
                    log.info("via of")

                events.of("minelark:server_started").on(greet)
                """);

        TestLog log = new TestLog();
        ServerResult result = StarlarkHost.runServer(dir, log);
        result.events().fire("minelark:server_started", log);

        assertTrue(log.anyMessageContains("via of"), "got " + log.messages);
    }

    @Test
    void typoedEventIsReportedNotThrown(@TempDir Path dir) throws IOException {
        write(dir, "e.star", """
                def f(ctx):
                    pass

                events.minelark.NOT_AN_EVENT.on(f)
                """);

        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, log);

        assertTrue(log.anyMessageContains("NOT_AN_EVENT"), "a typo'd event should be reported, got " + log.messages);
    }

    @Test
    void badPatternIsReportedNotThrown(@TempDir Path dir) throws IOException {
        write(dir, "r.star", """
                recipes.shaped("minelark:ruby", ["RR", "R"], {"R": "minelark:ruby"})
                """);

        TestLog log = new TestLog();
        List<RecipeSpec> recipes = run(dir, log);

        assertTrue(recipes.isEmpty());
        assertTrue(log.anyMessageContains("same length"), "got " + log.messages);
    }
}
