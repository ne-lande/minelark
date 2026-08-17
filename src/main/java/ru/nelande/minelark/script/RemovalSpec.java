package ru.nelande.minelark.script;

import java.util.List;

/**
 * A filter, declared by a server script via {@code recipes.remove({...})}, describing which existing
 * recipes to drop when the game loads its recipes. Every non-null field must match for a recipe to be
 * removed (fields are ANDed); a spec with all-null fields matches nothing (guarded at parse time).
 *
 * <p>Loader-agnostic: the adapter side (a {@code RecipeManager} mixin) reads each live recipe's id,
 * type, ingredient item ids, and result item id, and calls {@link #matches} with those plain strings.
 *
 * @param id     an exact recipe id (bare defaults to {@code minecraft:}), or null
 * @param mod    a namespace to match the recipe id's namespace, or null
 * @param type   a recipe type id, e.g. {@code minecraft:crafting_shaped}, or null
 * @param input  an ingredient item/tag id the recipe must use, or null
 * @param output the result item id the recipe must produce, or null
 */
public record RemovalSpec(String id, String mod, String type, String input, String output) {

    /**
     * Tests one live recipe against this filter.
     *
     * @param recipeId   the recipe's full id (e.g. {@code minecraft:oak_planks})
     * @param recipeType the recipe type's full id (e.g. {@code minecraft:crafting_shaped})
     * @param inputs     the full item/tag ids of the recipe's ingredients
     * @param outputId   the full item id of the recipe's result (may be null if none)
     * @return whether every set field of this filter matches
     */
    public boolean matches(String recipeId, String recipeType, List<String> inputs, String outputId) {
        if (id != null && !id.equals(recipeId)) {
            return false;
        }
        if (mod != null && !mod.equals(namespaceOf(recipeId))) {
            return false;
        }
        if (type != null && !type.equals(recipeType)) {
            return false;
        }
        if (input != null && (inputs == null || !inputs.contains(input))) {
            return false;
        }
        if (output != null && !output.equals(outputId)) {
            return false;
        }
        return true;
    }

    private static String namespaceOf(String id) {
        if (id == null) {
            return null;
        }
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(0, colon) : "minecraft";
    }
}
