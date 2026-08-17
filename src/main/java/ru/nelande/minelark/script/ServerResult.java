package ru.nelande.minelark.script;

import java.util.List;

/**
 * Everything the server-phase scripts produced: the reloadable data (recipes and recipe-removal
 * filters), the registered event callbacks, the registered custom commands, and how many top-level
 * scripts ran.
 */
public record ServerResult(List<RecipeSpec> recipes, List<RemovalSpec> recipeRemovals, Events events,
                           CommandsApi commands, int scriptCount) {
}
