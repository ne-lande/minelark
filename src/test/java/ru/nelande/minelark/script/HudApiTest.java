package ru.nelande.minelark.script;

import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1 tests for the client {@code hud} namespace (element collection; the drawing is adapter-side). */
class HudApiTest {

    private static void text(HudApi hud, String key, String content, int x, int y, String anchor,
            String color, boolean shadow) throws EvalException {
        hud.text(key, content, StarlarkInt.of(x), StarlarkInt.of(y), anchor, color, shadow);
    }

    @Test
    void collectsElementWithParsedFields() throws EvalException {
        HudApi hud = new HudApi();
        text(hud, "fps", "60 FPS", 4, 8, "top_right", "#00ff00", false);

        List<HudElement> elements = hud.elements();
        assertEquals(1, elements.size());
        HudElement e = elements.get(0);
        assertEquals("fps", e.key());
        assertEquals(4, e.x());
        assertEquals(8, e.y());
        assertEquals(HudAnchor.TOP_RIGHT, e.anchor());
        assertEquals(0xFF00FF00, e.color());   // opaque green
        assertFalse(e.shadow());
    }

    @Test
    void sameKeyReplacesAndKeepsOrder() throws EvalException {
        HudApi hud = new HudApi();
        text(hud, "a", "first", 0, 0, "top_left", "#ffffff", true);
        text(hud, "b", "second", 0, 10, "top_left", "#ffffff", true);
        text(hud, "a", "updated", 0, 0, "top_left", "#ffffff", true);

        assertEquals(List.of("a", "b"), hud.elements().stream().map(HudElement::key).toList());
    }

    @Test
    void removeAndClear() throws EvalException {
        HudApi hud = new HudApi();
        text(hud, "a", "x", 0, 0, "top_left", "#ffffff", true);
        assertTrue(hud.remove("a"));
        assertFalse(hud.remove("a"));
        text(hud, "b", "y", 0, 0, "top_left", "#ffffff", true);
        hud.clear();
        assertEquals(List.of(), hud.elements());
    }

    @Test
    void rejectsBadAnchorAndColor() {
        HudApi hud = new HudApi();
        assertThrows(EvalException.class,
                () -> text(hud, "a", "x", 0, 0, "middle_left", "#ffffff", true));
        assertThrows(EvalException.class,
                () -> text(hud, "a", "x", 0, 0, "center", "green", true));
    }

    @Test
    void usableFromClientScripts(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("h.star"), """
                hud.text("coords", text("0, 64, 0").color("aqua"), x = 2, y = 2, anchor = "bottom_left")
                """);

        TestLog log = new TestLog();
        ClientResult result = StarlarkHost.runClient(dir, NOOP_CLIENT, log);

        List<HudElement> elements = result.hud().elements();
        assertEquals(1, elements.size());
        assertEquals("coords", elements.get(0).key());
        assertEquals(HudAnchor.BOTTOM_LEFT, elements.get(0).anchor());
    }

    /** A client with no world - enough to run scripts that only touch the {@code hud} namespace. */
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
