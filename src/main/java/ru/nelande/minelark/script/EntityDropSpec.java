package ru.nelande.minelark.script;

/**
 * A replacement loot table for an entity, declared by {@code loot.entity_drops(...)}, already
 * serialised to JSON. Written to {@code data/<ns>/loot_table/entities/<path>.json} in the generated
 * pack, overriding the entity's vanilla drops.
 *
 * @param entityId the full entity type id ({@code namespace:path})
 * @param json     the loot-table JSON (1.21 format)
 */
public record EntityDropSpec(String entityId, String json) {
}
