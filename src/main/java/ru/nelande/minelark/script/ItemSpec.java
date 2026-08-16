package ru.nelande.minelark.script;

import java.util.List;

/**
 * A loader-agnostic description of an item declared by a startup script.
 *
 * <p>Deliberately free of any Minecraft types so the scripting layer can be built and tested
 * without the game. The Minecraft-facing code turns each {@code ItemSpec} into a real item.
 *
 * @param id           the item path, e.g. {@code "ruby"} (namespaced as {@code minelark:ruby})
 * @param maxStackSize the maximum stack size (vanilla allows 1..99); ignored when {@code maxDamage > 0}
 * @param maxDamage    durability; {@code > 0} makes the item damageable (and unstackable), {@code 0} = not
 * @param rarity       the name-colour tier
 * @param fireproof    whether the item survives fire and lava
 * @param nutrition    hunger restored when eaten; {@code > 0} makes the item edible, {@code 0} = not food
 * @param saturation   the food saturation modifier (only meaningful when {@code nutrition > 0})
 * @param burnTime     furnace burn time in ticks; {@code > 0} registers the item as fuel, {@code 0} = not
 * @param displayName  the shown name; empty means use the default {@code item.minelark.<id>} translation
 * @param tags         resolved item-tag ids ({@code namespace:path}) this item should belong to
 */
public record ItemSpec(
        String id,
        int maxStackSize,
        int maxDamage,
        Rarity rarity,
        boolean fireproof,
        int nutrition,
        double saturation,
        int burnTime,
        String displayName,
        List<String> tags
) {
    /** A plain stackable item with default rarity, no special flags, no name override, and no tags. */
    public static ItemSpec basic(String id, int maxStackSize) {
        return new ItemSpec(id, maxStackSize, 0, Rarity.COMMON, false, 0, 0.0, 0, "", List.of());
    }
}
