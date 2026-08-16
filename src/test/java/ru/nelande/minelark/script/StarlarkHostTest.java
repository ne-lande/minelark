package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the scripting engine end-to-end without Minecraft: real scripts through the real
 * interpreter, validating declaration collection, {@code console}, {@code load()} imports, and
 * error handling.
 */
class StarlarkHostTest {

    private static void write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void collectsDeclaredItems(@TempDir Path dir) throws IOException {
        write(dir, "a.star", """
                print("hi from script")
                item("ruby")
                item("sapphire", max_stack_size = 16)
                """);

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertEquals(List.of(ItemSpec.basic("ruby", 64), ItemSpec.basic("sapphire", 16)), specs);
        assertTrue(log.anyMessageContains("hi from script"), "print() should be captured, got " + log.messages);
    }

    @Test
    void consoleLogsWithLevelAndSource(@TempDir Path dir) throws IOException {
        write(dir, "log.star", """
                log.info("all good")
                log.warning("hmm")
                log.error("uh oh")
                """);

        TestLog log = new TestLog();
        StarlarkHost.runStartup(dir, log).items();

        assertEquals(ScriptLog.Level.INFO, log.levelOfMessageContaining("all good"));
        assertEquals(ScriptLog.Level.WARNING, log.levelOfMessageContaining("hmm"));
        assertEquals(ScriptLog.Level.ERROR, log.levelOfMessageContaining("uh oh"));
        assertTrue(log.anyMessageContains("[log.star]"), "console output should carry the script name, got " + log.messages);
    }

    @Test
    void loadImportsHelperModule(@TempDir Path dir) throws IOException {
        write(dir, "lib/gems.star", """
                DEFAULT_STACK = 16

                def gem(name):
                    item(name, max_stack_size = DEFAULT_STACK)
                """);
        write(dir, "pack.star", """
                load("lib/gems.star", "gem")
                gem("ruby")
                gem("sapphire")
                """);

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertEquals(List.of(ItemSpec.basic("ruby", 16), ItemSpec.basic("sapphire", 16)), specs);
    }

    @Test
    void loadCyclesAreReportedNotHung(@TempDir Path dir) throws IOException {
        write(dir, "main.star", "load(\"lib/a.star\", \"x\")\n");
        write(dir, "lib/a.star", "load(\"lib/b.star\", \"y\")\nx = 1\n");
        write(dir, "lib/b.star", "load(\"lib/a.star\", \"x\")\ny = 2\n");

        TestLog log = new TestLog();
        StarlarkHost.runStartup(dir, log).items();

        assertTrue(log.anyMessageContains("cycle"), "should report an import cycle, got " + log.messages);
    }

    @Test
    void loadCannotEscapeScriptsFolder(@TempDir Path dir) throws IOException {
        write(dir, "secret.star", "item(\"leaked\")\n");
        Path startup = dir.resolve("startup");
        write(startup, "evil.star", "load(\"../secret.star\", \"x\")\n");

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(startup, log).items();

        assertTrue(specs.isEmpty(), "the escaping load must not run the outside script");
        assertTrue(log.anyMessageContains("escapes"), "should report the escape attempt, got " + log.messages);
    }

    @Test
    void itemOptionsAreParsed(@TempDir Path dir) throws IOException {
        write(dir, "gear.star", """
                item("ruby_sword", max_damage = 250, rarity = "rare")
                item("phoenix_feather", fireproof = True, rarity = "epic")
                """);

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertEquals(2, specs.size(), "got " + log.messages);
        ItemSpec sword = specs.get(0);
        assertEquals("ruby_sword", sword.id());
        assertEquals(250, sword.maxDamage());
        assertEquals(Rarity.RARE, sword.rarity());

        ItemSpec feather = specs.get(1);
        assertTrue(feather.fireproof());
        assertEquals(Rarity.EPIC, feather.rarity());
    }

    @Test
    void foodAndFuelOptionsAreParsed(@TempDir Path dir) throws IOException {
        write(dir, "consumables.star", """
                item("trail_mix", nutrition = 6, saturation = 0.8)
                item("coal_chunk", burn_time = 1600)
                """);

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertEquals(2, specs.size(), "got " + log.messages);
        ItemSpec food = specs.get(0);
        assertEquals(6, food.nutrition());
        assertEquals(0.8, food.saturation(), 1e-6);
        assertEquals(1600, specs.get(1).burnTime());
    }

    @Test
    void displayNameAndTagsAreParsed(@TempDir Path dir) throws IOException {
        write(dir, "tagged.star", """
                item("ruby", display_name = "Ruby", tags = ["gems", "minecraft:beacon_payment_items"])
                block("marble", display_name = "Marble", tags = ["minecraft:mineable/pickaxe"])
                """);

        TestLog log = new TestLog();
        StartupResult r = StarlarkHost.runStartup(dir, log);

        ItemSpec ruby = r.items().get(0);
        assertEquals("Ruby", ruby.displayName());
        assertEquals(List.of("c:gems", "minecraft:beacon_payment_items"), ruby.tags());

        BlockSpec marble = r.blocks().get(0);
        assertEquals("Marble", marble.displayName());
        assertEquals(List.of("minecraft:mineable/pickaxe"), marble.tags());
    }

    @Test
    void invalidTagIsReportedNotThrown(@TempDir Path dir) throws IOException {
        write(dir, "bad.star", "item(\"x\", tags = [\"Bad Tag!\"])\n");

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertTrue(specs.isEmpty());
        assertTrue(log.anyMessageContains("tag"), "should log the error, got " + log.messages);
    }

    @Test
    void invalidRarityIsReportedNotThrown(@TempDir Path dir) throws IOException {
        write(dir, "bad.star", "item(\"x\", rarity = \"legendary\")\n");

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertTrue(specs.isEmpty());
        assertTrue(log.anyMessageContains("rarity"), "should log the error, got " + log.messages);
    }

    @Test
    void invalidStackSizeIsReportedNotThrown(@TempDir Path dir) throws IOException {
        write(dir, "bad.star", "item(\"boom\", max_stack_size = 500)\n");

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertTrue(specs.isEmpty());
        assertTrue(log.anyMessageContains("max_stack_size"), "should log the error, got " + log.messages);
    }

    @Test
    void invalidIdIsReportedNotThrown(@TempDir Path dir) throws IOException {
        write(dir, "bad.star", "item(\"Not Valid!\")\n");

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertTrue(specs.isEmpty());
        assertTrue(log.anyMessageContains("is invalid"), "should log the error, got " + log.messages);
    }

    @Test
    void syntaxErrorsAreReported(@TempDir Path dir) throws IOException {
        write(dir, "broken.star", "item(\n");

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertTrue(specs.isEmpty());
        assertTrue(log.anyMessageContains("syntax error"), "should log a syntax error, got " + log.messages);
    }

    @Test
    void oneBrokenScriptDoesNotStopOthers(@TempDir Path dir) throws IOException {
        write(dir, "a_broken.star", "item(\"UPPER\")\n");
        write(dir, "b_good.star", "item(\"ruby\")\n");

        TestLog log = new TestLog();
        List<ItemSpec> specs = StarlarkHost.runStartup(dir, log).items();

        assertEquals(List.of(ItemSpec.basic("ruby", 64)), specs);
        assertFalse(log.messages.isEmpty());
    }

    @Test
    void collectsDeclaredBlocks(@TempDir Path dir) throws IOException {
        write(dir, "blocks.star", """
                block("marble")
                block("glow_crystal", hardness = 0.3, luminance = 15, requires_tool = True)
                """);

        TestLog log = new TestLog();
        List<BlockSpec> blocks = StarlarkHost.runStartup(dir, log).blocks();

        assertEquals(2, blocks.size(), "got " + log.messages);
        assertEquals(BlockSpec.basic("marble"), blocks.get(0));
        BlockSpec crystal = blocks.get(1);
        assertEquals(15, crystal.luminance());
        assertTrue(crystal.requiresTool());
        assertEquals(0.3, crystal.hardness(), 1e-6);
    }

    @Test
    void itemHandleIsUsableInBlockDrops(@TempDir Path dir) throws IOException {
        write(dir, "h.star", """
                ruby = item("ruby")
                block("ruby_ore", drops = ruby)
                """);

        StartupResult r = StarlarkHost.runStartup(dir, new TestLog());

        assertEquals("minelark:ruby", r.blocks().get(0).drops(), "the item handle should resolve to its id");
    }

    @Test
    void blockDropsAreParsed(@TempDir Path dir) throws IOException {
        write(dir, "b.star", """
                block("self_dropper")
                block("silent", drops = "none")
                block("gem_ore", drops = "minelark:ruby")
                """);

        List<BlockSpec> blocks = StarlarkHost.runStartup(dir, new TestLog()).blocks();

        assertEquals("", blocks.get(0).drops(), "empty drops means self-drop");
        assertEquals("none", blocks.get(1).drops());
        assertEquals("minelark:ruby", blocks.get(2).drops());
    }

    @Test
    void invalidLuminanceIsReportedNotThrown(@TempDir Path dir) throws IOException {
        write(dir, "bad.star", "block(\"x\", luminance = 20)\n");

        TestLog log = new TestLog();
        List<BlockSpec> blocks = StarlarkHost.runStartup(dir, log).blocks();

        assertTrue(blocks.isEmpty());
        assertTrue(log.anyMessageContains("luminance"), "should log the error, got " + log.messages);
    }

    @Test
    void missingDirectoryYieldsEmpty(@TempDir Path dir) {
        List<ItemSpec> specs = StarlarkHost.runStartup(dir.resolve("does-not-exist"), new TestLog()).items();
        assertTrue(specs.isEmpty());
    }
}
