package ru.nelande.minelark.script;

import java.util.List;

/**
 * An injection of extra drops into an existing loot table, declared by {@code loot.inject(...)}.
 * Applied at loot-table load time by the adapter (a {@code LootTableEvents.MODIFY} handler builds a
 * pool from the drops), so it adds to a table without replacing it.
 *
 * @param tableId the loot table id to add to (e.g. {@code minecraft:chests/simple_dungeon})
 * @param drops   the extra drops to add as one pool
 */
public record LootInjectSpec(String tableId, List<LootDrop> drops) {
}
