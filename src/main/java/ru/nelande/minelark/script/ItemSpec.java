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
 * @param toolType     tool kind ({@code pickaxe|axe|shovel|hoe|sword}); empty means not a tool
 * @param toolTier     tool material tier ({@code wood|stone|iron|gold|diamond|netherite}); empty when not a tool
 * @param armorSlot    armor slot ({@code helmet|chestplate|leggings|boots}); empty means not armor
 * @param armorMaterial armor material ({@code leather|chainmail|iron|gold|diamond|netherite|turtle}); empty when not armor
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
        List<String> tags,
        String toolType,
        String toolTier,
        String armorSlot,
        String armorMaterial
) {
    /** A plain stackable item with default rarity, no special flags, no name override, and no tags. */
    public static ItemSpec basic(String id, int maxStackSize) {
        return new ItemSpec(id, maxStackSize, 0, Rarity.COMMON, false, 0, 0.0, 0, "", List.of(),
                "", "", "", "");
    }

    /** Whether this item is a tool (has a {@code toolType}). Tools use a handheld inventory model. */
    public boolean isTool() {
        return !toolType.isEmpty();
    }

    /** Whether this item is a piece of armor (has an {@code armorSlot}). */
    public boolean isArmor() {
        return !armorSlot.isEmpty();
    }
}
