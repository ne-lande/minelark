package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for {@link PushBundleBuilder}: only flag-directive-carrying top-level scripts are
 * offered, subfolder helpers ride along, capabilities are parsed, hashes match the bodies, and the
 * size/count caps are enforced.
 */
class PushBundleBuilderTest {

    private static void write(Path file, String body) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
    }

    @Test
    void onlyFlaggedTopLevelScriptsAreEntries(@TempDir Path dir) throws IOException {
        write(dir.resolve("hud.star"), "# minelark: push\nhud.text('k', 'hi')\n");
        write(dir.resolve("wip.star"), "# not flagged\nlog.info('nope')\n");
        write(dir.resolve("lib/helpers.star"), "def helper():\n    return 1\n");

        PushBundle bundle = new PushBundleBuilder().build(dir);

        assertFalse(bundle.isEmpty());
        assertTrue(bundle.bodies().containsKey("hud.star"), "flagged entry is included");
        assertTrue(bundle.bodies().containsKey("lib/helpers.star"), "subfolder helper rides along");
        assertFalse(bundle.bodies().containsKey("wip.star"), "unflagged top-level file is not exposed");
    }

    @Test
    void allUnflaggedFolderOffersNothing(@TempDir Path dir) throws IOException {
        write(dir.resolve("a.star"), "log.info('a')\n");
        write(dir.resolve("lib/b.star"), "x = 1\n");
        PushBundle bundle = new PushBundleBuilder().build(dir);
        assertTrue(bundle.isEmpty());
        assertSame(PushBundle.EMPTY, bundle);
    }

    private static void assertSame(Object expected, Object actual) {
        assertTrue(expected == actual, "expected the same instance");
    }

    @Test
    void missingFolderIsEmpty(@TempDir Path dir) {
        PushBundle bundle = new PushBundleBuilder().build(dir.resolve("does-not-exist"));
        assertTrue(bundle.isEmpty());
    }

    @Test
    void capabilitiesAreParsedAndUnioned(@TempDir Path dir) throws IOException {
        write(dir.resolve("visual.star"), "# minelark: push\nhud.text('k', 'hi')\n");
        write(dir.resolve("live.star"), "# minelark: push capabilities=hud,net\nnet.on('c', None)\n");

        PushBundle bundle = new PushBundleBuilder().build(dir);
        Set<Capability> requested = bundle.manifest().requested();

        // visual.star contributes the visual defaults; live.star adds NET on top.
        assertTrue(requested.contains(Capability.HUD));
        assertTrue(requested.contains(Capability.NET));
        assertTrue(requested.contains(Capability.CLIENT_READ), "visual defaults are contributed");
        assertFalse(requested.contains(Capability.CHAT), "chat is never requested unless declared");
    }

    @Test
    void hashesMatchTheBodies(@TempDir Path dir) throws IOException {
        String body = "# minelark: push\nhud.text('k', 'hi')\n";
        write(dir.resolve("hud.star"), body);
        PushBundle bundle = new PushBundleBuilder().build(dir);
        PushManifest.Entry entry = bundle.manifest().entries().stream()
                .filter(e -> e.name().equals("hud.star")).findFirst().orElseThrow();
        assertEquals(Sha256.hex(bundle.bodies().get("hud.star")), entry.sha256());
        assertEquals(Sha256.hex(body), entry.sha256());
    }

    @Test
    void fileCountCapIsEnforced(@TempDir Path dir) throws IOException {
        write(dir.resolve("a.star"), "# minelark: push\nx = 1\n");
        write(dir.resolve("b.star"), "# minelark: push\ny = 2\n");
        write(dir.resolve("c.star"), "# minelark: push\nz = 3\n");
        assertThrows(PushBundleBuilder.TooLarge.class, () -> new PushBundleBuilder(2, 1_000_000L).build(dir));
    }

    @Test
    void totalSizeCapIsEnforced(@TempDir Path dir) throws IOException {
        write(dir.resolve("big.star"), "# minelark: push\n" + "x = 1  # padding padding padding\n".repeat(50));
        assertThrows(PushBundleBuilder.TooLarge.class, () -> new PushBundleBuilder(64, 100L).build(dir));
    }
}
