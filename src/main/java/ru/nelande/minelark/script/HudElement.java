package ru.nelande.minelark.script;

/**
 * One keyed on-screen HUD element a client script placed with {@code hud.text(...)}. MC-agnostic: the
 * {@code content} is a {@link MineText}, {@code color} is a packed {@code 0xAARRGGBB} used as the base
 * tint (segments of {@code content} with their own colour keep it), and the adapter renders it each
 * frame at {@code anchor} plus the {@code x}/{@code y} offset.
 */
public record HudElement(String key, MineText content, int x, int y, HudAnchor anchor, int color, boolean shadow) {
}
