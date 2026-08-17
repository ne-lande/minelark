package ru.nelande.minelark.script;

/**
 * One parsed drop entry for a loot table or injection: an item, how many, and a drop chance.
 * MC-agnostic - the entity-drop loot JSON is built from these, and the adapter builds a loot pool
 * from them for injections.
 *
 * @param itemId the full item id ({@code namespace:path})
 * @param min    minimum count (>= 1)
 * @param max    maximum count (>= min)
 * @param chance drop chance in (0, 1]; 1.0 means always
 */
public record LootDrop(String itemId, int min, int max, double chance) {
}
