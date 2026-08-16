package ru.nelande.minelark.script;

import java.util.List;

/**
 * Everything the server-phase scripts produced: the reloadable data (recipes), the registered event
 * callbacks, and how many top-level scripts ran.
 */
public record ServerResult(List<RecipeSpec> recipes, Events events, int scriptCount) {
}
