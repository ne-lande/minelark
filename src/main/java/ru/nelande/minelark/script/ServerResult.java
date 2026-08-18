package ru.nelande.minelark.script;

import java.util.List;

/**
 * Everything the server-phase scripts produced: the reloadable data (recipes and recipe-removal
 * filters, explicit tags, entity-drop replacements, loot-table injections, generic datapack JSON),
 * the registered event callbacks, the registered custom commands, the {@code net} channel handlers,
 * the {@code timers} scheduler, and how many top-level scripts ran.
 */
public record ServerResult(
        List<RecipeSpec> recipes,
        List<RemovalSpec> recipeRemovals,
        List<TagSpec> tags,
        List<EntityDropSpec> entityDrops,
        List<LootInjectSpec> lootInjects,
        List<DatapackJsonSpec> datapackJson,
        Events events,
        CommandsApi commands,
        ServerNetworkApi network,
        Scheduler scheduler,
        int scriptCount) {
}
