package ru.nelande.minelark.script;

import java.util.List;

/**
 * One keyed on-screen HUD element a client script placed. A sealed set of shapes - text, rectangle,
 * progress bar, texture, item icon, pie chart, and bar graph - all positioned by {@code anchor} plus
 * an {@code x}/{@code y} offset. MC-agnostic: colours are packed {@code 0xAARRGGBB}, textures and
 * items are id strings; the client adapter turns each variant into draw calls.
 */
public sealed interface HudElement {
    String key();

    int x();

    int y();

    HudAnchor anchor();

    /** A line of text ({@code content} carries its own styling; {@code color} is the base tint). */
    record Text(String key, MineText content, int x, int y, HudAnchor anchor, int color, boolean shadow)
            implements HudElement {
    }

    /** A filled rectangle. */
    record Rect(String key, int x, int y, int width, int height, int color, HudAnchor anchor)
            implements HudElement {
    }

    /** A horizontal progress bar: {@code background} behind, {@code color} filled to {@code progress} (0..1). */
    record Bar(String key, int x, int y, int width, int height, double progress, int color, int background,
            HudAnchor anchor) implements HudElement {
    }

    /** A texture drawn at {@code width}x{@code height} (a GUI sprite id). */
    record Image(String key, String texture, int x, int y, int width, int height, HudAnchor anchor)
            implements HudElement {
    }

    /** An item's icon (16x16) by item id. */
    record Item(String key, String itemId, int x, int y, HudAnchor anchor) implements HudElement {
    }

    /** A pie chart: slices are drawn clockwise from the top, sized by their share of the total. */
    record Pie(String key, int x, int y, int radius, List<Slice> slices, HudAnchor anchor)
            implements HudElement {
        /** One wedge: its {@code value} (relative to the sum) and its packed colour. */
        public record Slice(double value, int color) {
        }
    }

    /** A bar graph of {@code values} (scaled to the tallest), like the F3 frame-time graph. */
    record Graph(String key, int x, int y, int width, int height, List<Double> values, int color,
            HudAnchor anchor) implements HudElement {
    }
}
