package ru.nelande.minelark.script;

/**
 * A recipe declared by a server script, already serialised to datapack JSON.
 *
 * <p>Loader-agnostic: the JSON is written verbatim into the generated data pack at
 * {@code data/minelark/recipe/<idPath>.json}.
 *
 * @param idPath the recipe path under the {@code minelark} namespace, e.g. {@code "shaped/0"}
 * @param json   the recipe JSON (1.21 format)
 */
public record RecipeSpec(String idPath, String json) {
}
