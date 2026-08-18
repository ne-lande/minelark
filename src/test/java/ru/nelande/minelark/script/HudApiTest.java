package ru.nelande.minelark.script;

import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkFloat;
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

    private static HudElement only(HudApi hud) {
        List<HudElement> elements = hud.elements();
        assertEquals(1, elements.size());
        return elements.get(0);
    }

    @Test
    void textElement() throws EvalException {
        HudApi hud = new HudApi();
        hud.text("fps", "60 FPS", StarlarkInt.of(4), StarlarkInt.of(8), "top_right", "#00ff00", false);

        HudElement.Text e = (HudElement.Text) only(hud);
        assertEquals("fps", e.key());
        assertEquals(4, e.x());
        assertEquals(8, e.y());
        assertEquals(HudAnchor.TOP_RIGHT, e.anchor());
        assertEquals(0xFF00FF00, e.color());
        assertFalse(e.shadow());
    }

    @Test
    void rectElementKeepsAlpha() throws EvalException {
        HudApi hud = new HudApi();
        hud.rect("panel", StarlarkInt.of(1), StarlarkInt.of(2), StarlarkInt.of(30), StarlarkInt.of(10),
                "#80112233", "center");

        HudElement.Rect e = (HudElement.Rect) only(hud);
        assertEquals(30, e.width());
        assertEquals(10, e.height());
        assertEquals(0x80112233, e.color());   // #aarrggbb alpha preserved
        assertEquals(HudAnchor.CENTER, e.anchor());
    }

    @Test
    void barElement() throws EvalException {
        HudApi hud = new HudApi();
        hud.bar("hp", StarlarkInt.of(0), StarlarkInt.of(0), StarlarkInt.of(50), StarlarkInt.of(6),
                StarlarkFloat.of(0.5), "#ff0000", "#000000", "top_left");

        HudElement.Bar e = (HudElement.Bar) only(hud);
        assertEquals(0.5, e.progress());
        assertEquals(50, e.width());
        assertEquals(0xFFFF0000, e.color());
    }

    @Test
    void itemElement() throws EvalException {
        HudApi hud = new HudApi();
        hud.item("icon", "minecraft:diamond", StarlarkInt.of(2), StarlarkInt.of(2), "top_left");

        HudElement.Item e = (HudElement.Item) only(hud);
        assertEquals("minecraft:diamond", e.itemId());
    }

    @Test
    void pieElement() throws EvalException {
        HudApi hud = new HudApi();
        Object slices = StarlarkJson.fromJsonString(
                "[{\"value\": 3, \"color\": \"#ff0000\"}, {\"value\": 1, \"color\": \"#00ff00\"}]");
        hud.pie("chart", StarlarkInt.of(0), StarlarkInt.of(0), StarlarkInt.of(20), slices, "top_left");

        HudElement.Pie e = (HudElement.Pie) only(hud);
        assertEquals(20, e.radius());
        assertEquals(2, e.slices().size());
        assertEquals(3.0, e.slices().get(0).value());
        assertEquals(0xFFFF0000, e.slices().get(0).color());
    }

    @Test
    void graphElement() throws EvalException {
        HudApi hud = new HudApi();
        Object values = StarlarkJson.fromJsonString("[1, 2, 3, 2]");
        hud.graph("g", StarlarkInt.of(0), StarlarkInt.of(0), StarlarkInt.of(40), StarlarkInt.of(10),
                values, "#56b6c2", "top_left");

        HudElement.Graph e = (HudElement.Graph) only(hud);
        assertEquals(List.of(1.0, 2.0, 3.0, 2.0), e.values());
    }

    @Test
    void sameKeyReplacesAcrossTypesAndKeepsOrder() throws EvalException {
        HudApi hud = new HudApi();
        hud.text("a", "first", StarlarkInt.of(0), StarlarkInt.of(0), "top_left", "#ffffff", true);
        hud.rect("b", StarlarkInt.of(0), StarlarkInt.of(0), StarlarkInt.of(5), StarlarkInt.of(5), "#ffffff", "top_left");
        // Reusing key "a" with a different shape replaces it in place.
        hud.rect("a", StarlarkInt.of(0), StarlarkInt.of(0), StarlarkInt.of(5), StarlarkInt.of(5), "#ffffff", "top_left");

        List<HudElement> elements = hud.elements();
        assertEquals(List.of("a", "b"), elements.stream().map(HudElement::key).toList());
        assertTrue(elements.get(0) instanceof HudElement.Rect, "key a is now a rect");
    }

    @Test
    void removeAndClear() throws EvalException {
        HudApi hud = new HudApi();
        hud.text("a", "x", StarlarkInt.of(0), StarlarkInt.of(0), "top_left", "#ffffff", true);
        assertTrue(hud.remove("a"));
        assertFalse(hud.remove("a"));
        hud.rect("b", StarlarkInt.of(0), StarlarkInt.of(0), StarlarkInt.of(5), StarlarkInt.of(5), "#ffffff", "top_left");
        hud.clear();
        assertEquals(List.of(), hud.elements());
    }

    @Test
    void rejectsBadAnchorColorAndSlices() {
        HudApi hud = new HudApi();
        assertThrows(EvalException.class,
                () -> hud.text("a", "x", StarlarkInt.of(0), StarlarkInt.of(0), "middle_left", "#ffffff", true));
        assertThrows(EvalException.class,
                () -> hud.text("a", "x", StarlarkInt.of(0), StarlarkInt.of(0), "center", "green", true));
        assertThrows(EvalException.class,
                () -> hud.pie("p", StarlarkInt.of(0), StarlarkInt.of(0), StarlarkInt.of(10), "not a list", "top_left"));
    }

    @Test
    void usableFromClientScripts(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("h.star"), """
                hud.text("coords", text("0, 64, 0").color("aqua"), x = 2, y = 2, anchor = "bottom_left")
                hud.bar("hp", 2, 12, 60, 6, 0.75, color = "#ff5555")
                hud.pie("split", 2, 22, 16, [{"value": 2, "color": "#e06c75"}, {"value": 1, "color": "#98c379"}])
                """);

        TestLog log = new TestLog();
        ClientResult result = StarlarkHost.runClient(dir, NOOP_CLIENT, log);

        List<HudElement> elements = result.hud().elements();
        assertEquals(3, elements.size());
        assertTrue(elements.stream().anyMatch(e -> e instanceof HudElement.Pie), "pie collected");
        assertTrue(elements.stream().anyMatch(e -> e instanceof HudElement.Bar), "bar collected");
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
