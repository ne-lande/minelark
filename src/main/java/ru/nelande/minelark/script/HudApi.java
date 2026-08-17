package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The {@code hud} namespace for client scripts: draw your own always-on text onto the game screen
 * (unlike {@code debug}, which only shows on the F3 overlay). Each element is keyed, so setting the
 * same key again replaces it - handy from a per-tick callback (an fps meter, a coordinate readout).
 * MC-agnostic: it just collects {@link HudElement}s; the adapter renders {@link #elements()} each
 * frame. The client and the render thread are the same client thread, so a plain map is safe.
 */
public final class HudApi implements StarlarkValue {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private final Map<String, HudElement> entries = new LinkedHashMap<>();

    @StarlarkMethod(
            name = "text",
            doc = "Adds or replaces a keyed line of on-screen text. `content` is a string or a "
                    + "`text(...)` component; `x`/`y` are pixel offsets from `anchor`. Call it again "
                    + "with the same key (e.g. every tick) to update the line.",
            parameters = {
                    @Param(name = "key", doc = "A stable key identifying this element."),
                    @Param(name = "content", doc = "A string or a `text(...)` component to draw."),
                    @Param(name = "x", named = true, defaultValue = "0",
                            doc = "Horizontal pixel offset from the anchor."),
                    @Param(name = "y", named = true, defaultValue = "0",
                            doc = "Vertical pixel offset from the anchor."),
                    @Param(name = "anchor", named = true, defaultValue = "\"top_left\"",
                            doc = "One of `top_left`, `top_right`, `bottom_left`, `bottom_right`, `center`."),
                    @Param(name = "color", named = true, defaultValue = "\"#ffffff\"",
                            doc = "Base colour as `#rrggbb` (segments of a `text(...)` with their own "
                                    + "colour keep it)."),
                    @Param(name = "shadow", named = true, defaultValue = "True",
                            doc = "Whether to draw the usual text drop-shadow."),
            })
    public void text(String key, Object content, StarlarkInt x, StarlarkInt y, String anchor,
            String color, boolean shadow) throws EvalException {
        entries.put(key, new HudElement(
                key,
                MineText.coerce(content),
                x.toIntUnchecked(),
                y.toIntUnchecked(),
                HudAnchor.fromName(anchor),
                parseColor(color),
                shadow));
    }

    @StarlarkMethod(
            name = "remove",
            doc = "Removes a HUD element previously added with `text`. Returns whether one was removed.",
            parameters = {@Param(name = "key", doc = "The key passed to `text`.")})
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

    /** Parses {@code #rrggbb} into a packed {@code 0xFFrrggbb} (opaque), the form the renderer wants. */
    private static int parseColor(String value) throws EvalException {
        if (!HEX.matcher(value).matches()) {
            throw Starlark.errorf("invalid colour '%s' (expected #rrggbb)", value);
        }
        return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
    }
}
