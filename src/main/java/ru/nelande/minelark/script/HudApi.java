package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The {@code hud} namespace for client scripts: draw your own always-on graphics onto the game screen
 * (unlike {@code debug}, which only shows on the F3 overlay). Every element is keyed, so setting the
 * same key again replaces it - handy from a per-tick callback (an fps meter, a health bar, a live pie
 * chart). MC-agnostic: it just collects {@link HudElement}s; the adapter renders {@link #elements()}
 * each frame. The client and the render thread are the same client thread, so a plain map is safe.
 */
public final class HudApi implements StarlarkValue {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private final Map<String, HudElement> entries = new LinkedHashMap<>();

    @StarlarkMethod(
            name = "text",
            doc = "Adds or replaces a keyed line of on-screen text. `content` is a string or a "
                    + "`text(...)` component; `x`/`y` are pixel offsets from `anchor`.",
            parameters = {
                    @Param(name = "key", doc = "A stable key identifying this element."),
                    @Param(name = "content", doc = "A string or a `text(...)` component to draw."),
                    @Param(name = "x", named = true, defaultValue = "0", doc = "Horizontal offset from the anchor."),
                    @Param(name = "y", named = true, defaultValue = "0", doc = "Vertical offset from the anchor."),
                    @Param(name = "anchor", named = true, defaultValue = "\"top_left\"", doc = ANCHOR_DOC),
                    @Param(name = "color", named = true, defaultValue = "\"#ffffff\"",
                            doc = "Base colour as `#rrggbb`."),
                    @Param(name = "shadow", named = true, defaultValue = "True", doc = "Draw a drop-shadow."),
            })
    public void text(String key, Object content, StarlarkInt x, StarlarkInt y, String anchor,
            String color, boolean shadow) throws EvalException {
        put(new HudElement.Text(key, MineText.coerce(content), i(x), i(y), anchor(anchor), color(color), shadow));
    }

    @StarlarkMethod(
            name = "rect",
            doc = "Adds or replaces a filled rectangle - useful as a panel or background.",
            parameters = {
                    @Param(name = "key", doc = "A stable key identifying this element."),
                    @Param(name = "x", doc = "Horizontal offset from the anchor."),
                    @Param(name = "y", doc = "Vertical offset from the anchor."),
                    @Param(name = "width", doc = "Width in pixels."),
                    @Param(name = "height", doc = "Height in pixels."),
                    @Param(name = "color", named = true, defaultValue = "\"#ffffff\"",
                            doc = "Fill colour as `#rrggbb` or `#aarrggbb` (with alpha)."),
                    @Param(name = "anchor", named = true, defaultValue = "\"top_left\"", doc = ANCHOR_DOC),
            })
    public void rect(String key, StarlarkInt x, StarlarkInt y, StarlarkInt width, StarlarkInt height,
            String color, String anchor) throws EvalException {
        put(new HudElement.Rect(key, i(x), i(y), i(width), i(height), color(color), anchor(anchor)));
    }

    @StarlarkMethod(
            name = "bar",
            doc = "Adds or replaces a horizontal progress bar filled to `progress` (0.0 to 1.0).",
            parameters = {
                    @Param(name = "key", doc = "A stable key identifying this element."),
                    @Param(name = "x", doc = "Horizontal offset from the anchor."),
                    @Param(name = "y", doc = "Vertical offset from the anchor."),
                    @Param(name = "width", doc = "Width in pixels."),
                    @Param(name = "height", doc = "Height in pixels."),
                    @Param(name = "progress", doc = "How full, from 0.0 to 1.0."),
                    @Param(name = "color", named = true, defaultValue = "\"#55ff55\"",
                            doc = "Fill colour as `#rrggbb`."),
                    @Param(name = "background", named = true, defaultValue = "\"#000000\"",
                            doc = "Background colour as `#rrggbb` or `#aarrggbb`."),
                    @Param(name = "anchor", named = true, defaultValue = "\"top_left\"", doc = ANCHOR_DOC),
            })
    public void bar(String key, StarlarkInt x, StarlarkInt y, StarlarkInt width, StarlarkInt height,
            Object progress, String color, String background, String anchor) throws EvalException {
        put(new HudElement.Bar(key, i(x), i(y), i(width), i(height),
                toDouble(progress), color(color), color(background), anchor(anchor)));
    }

    @StarlarkMethod(
            name = "image",
            doc = "Adds or replaces a texture drawn at `width`x`height`. `texture` is a GUI sprite id.",
            parameters = {
                    @Param(name = "key", doc = "A stable key identifying this element."),
                    @Param(name = "texture", doc = "A GUI texture/sprite id, e.g. `\"minecraft:textures/gui/title/mojangstudios.png\"`."),
                    @Param(name = "x", doc = "Horizontal offset from the anchor."),
                    @Param(name = "y", doc = "Vertical offset from the anchor."),
                    @Param(name = "width", doc = "Width in pixels."),
                    @Param(name = "height", doc = "Height in pixels."),
                    @Param(name = "anchor", named = true, defaultValue = "\"top_left\"", doc = ANCHOR_DOC),
            })
    public void image(String key, String texture, StarlarkInt x, StarlarkInt y, StarlarkInt width,
            StarlarkInt height, String anchor) throws EvalException {
        put(new HudElement.Image(key, texture, i(x), i(y), i(width), i(height), anchor(anchor)));
    }

    @StarlarkMethod(
            name = "item",
            doc = "Adds or replaces a 16x16 item icon by item id (works for scripted items too).",
            parameters = {
                    @Param(name = "key", doc = "A stable key identifying this element."),
                    @Param(name = "item", doc = "An item id, e.g. `\"minecraft:diamond\"` or `\"minelark:ruby\"`."),
                    @Param(name = "x", doc = "Horizontal offset from the anchor."),
                    @Param(name = "y", doc = "Vertical offset from the anchor."),
                    @Param(name = "anchor", named = true, defaultValue = "\"top_left\"", doc = ANCHOR_DOC),
            })
    public void item(String key, String item, StarlarkInt x, StarlarkInt y, String anchor)
            throws EvalException {
        put(new HudElement.Item(key, item, i(x), i(y), anchor(anchor)));
    }

    @StarlarkMethod(
            name = "pie",
            doc = "Adds or replaces a pie chart. `slices` is a list of `{\"value\": n, \"color\": "
                    + "\"#rrggbb\"}` dicts, drawn clockwise from the top sized by their share of the total.",
            parameters = {
                    @Param(name = "key", doc = "A stable key identifying this element."),
                    @Param(name = "x", doc = "Horizontal offset from the anchor."),
                    @Param(name = "y", doc = "Vertical offset from the anchor."),
                    @Param(name = "radius", doc = "Radius in pixels."),
                    @Param(name = "slices", doc = "A list of `{value, color}` dicts."),
                    @Param(name = "anchor", named = true, defaultValue = "\"top_left\"", doc = ANCHOR_DOC),
            })
    public void pie(String key, StarlarkInt x, StarlarkInt y, StarlarkInt radius, Object slices, String anchor)
            throws EvalException {
        put(new HudElement.Pie(key, i(x), i(y), i(radius), parseSlices(slices), anchor(anchor)));
    }

    @StarlarkMethod(
            name = "graph",
            doc = "Adds or replaces a bar graph of `values` (scaled to the tallest), like the F3 graph.",
            parameters = {
                    @Param(name = "key", doc = "A stable key identifying this element."),
                    @Param(name = "x", doc = "Horizontal offset from the anchor."),
                    @Param(name = "y", doc = "Vertical offset from the anchor."),
                    @Param(name = "width", doc = "Width in pixels."),
                    @Param(name = "height", doc = "Height in pixels."),
                    @Param(name = "values", doc = "A list of numbers."),
                    @Param(name = "color", named = true, defaultValue = "\"#56b6c2\"",
                            doc = "Bar colour as `#rrggbb`."),
                    @Param(name = "anchor", named = true, defaultValue = "\"top_left\"", doc = ANCHOR_DOC),
            })
    public void graph(String key, StarlarkInt x, StarlarkInt y, StarlarkInt width, StarlarkInt height,
            Object values, String color, String anchor) throws EvalException {
        put(new HudElement.Graph(key, i(x), i(y), i(width), i(height),
                parseValues(values), color(color), anchor(anchor)));
    }

    @StarlarkMethod(
            name = "remove",
            doc = "Removes a HUD element previously added. Returns whether one was removed.",
            parameters = {@Param(name = "key", doc = "The key the element was added under.")})
    public boolean remove(String key) {
        return entries.remove(key) != null;
    }

    @StarlarkMethod(name = "clear", doc = "Removes all HUD elements added by scripts.")
    public void clear() {
        entries.clear();
    }

    /** The current elements in insertion order, for the adapter to draw each frame. */
    public List<HudElement> elements() {
        return List.copyOf(entries.values());
    }

    private static final String ANCHOR_DOC =
            "One of `top_left`, `top_right`, `bottom_left`, `bottom_right`, `center`.";

    private void put(HudElement element) {
        entries.put(element.key(), element);
    }

    private static int i(StarlarkInt value) {
        return value.toIntUnchecked();
    }

    private static HudAnchor anchor(String name) throws EvalException {
        return HudAnchor.fromName(name);
    }

    /** Parses {@code #rrggbb} or {@code #aarrggbb} into packed {@code 0xAARRGGBB} (opaque if no alpha). */
    private static int color(String value) throws EvalException {
        if (value.length() == 9 && value.charAt(0) == '#') {
            try {
                return (int) Long.parseLong(value.substring(1), 16);
            } catch (NumberFormatException ignored) {
                // fall through to the error below
            }
        }
        if (!HEX.matcher(value).matches()) {
            throw Starlark.errorf("invalid colour '%s' (expected #rrggbb or #aarrggbb)", value);
        }
        return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
    }

    private static double toDouble(Object value) throws EvalException {
        if (value instanceof StarlarkInt i) {
            return i.toIntUnchecked();
        }
        if (value instanceof StarlarkFloat f) {
            return f.toDouble();
        }
        throw Starlark.errorf("expected a number, got %s", Starlark.type(value));
    }

    private static List<HudElement.Pie.Slice> parseSlices(Object slices) throws EvalException {
        if (!(slices instanceof Sequence<?> sequence)) {
            throw Starlark.errorf("pie slices must be a list, got %s", Starlark.type(slices));
        }
        List<HudElement.Pie.Slice> result = new ArrayList<>();
        for (Object item : sequence) {
            if (!(item instanceof Dict<?, ?> dict)) {
                throw Starlark.errorf("each pie slice must be a dict {value, color}, got %s", Starlark.type(item));
            }
            Object value = dict.get("value");
            Object color = dict.get("color");
            if (value == null || color == null) {
                throw Starlark.errorf("each pie slice needs a 'value' and a 'color'");
            }
            result.add(new HudElement.Pie.Slice(toDouble(value), color(String.valueOf(color))));
        }
        return result;
    }

    private static List<Double> parseValues(Object values) throws EvalException {
        if (!(values instanceof Sequence<?> sequence)) {
            throw Starlark.errorf("graph values must be a list, got %s", Starlark.type(values));
        }
        List<Double> result = new ArrayList<>();
        for (Object item : sequence) {
            result.add(toDouble(item));
        }
        return result;
    }
}
