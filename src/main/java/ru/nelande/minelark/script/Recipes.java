package ru.nelande.minelark.script;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The {@code recipes} namespace for <b>server</b> scripts: declares crafting and cooking recipes,
 * serialised straight to 1.21 datapack JSON. Ids and tags without a namespace default to
 * {@code minecraft:}; prefix a tag with {@code #}, e.g. {@code "#c:ingots/copper"}.
 *
 * <pre>{@code
 * recipes.shaped("minelark:ruby_block", ["RRR", "RRR", "RRR"], {"R": "minelark:ruby"})
 * recipes.shapeless("4 minecraft:torch", ["minecraft:coal", "#minecraft:planks"])   # (count via API)
 * recipes.smelting("minelark:ruby", "minelark:ruby_ore", experience = 1.0)
 * }</pre>
 */
public final class Recipes implements StarlarkValue {
    private static final Gson GSON = new Gson();
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private final List<RecipeSpec> recipes = new ArrayList<>();
    private final List<RemovalSpec> removals = new ArrayList<>();
    private int counter = 0;

    @StarlarkMethod(
            name = "shaped",
            doc = "Adds a shaped crafting recipe. `pattern` is 1-3 rows of up to 3 characters; `key` maps "
                    + "each character to an item or `#tag`. A space means an empty slot.",
            parameters = {
                    @Param(name = "result", doc = "The output item id (namespaced as minecraft: by default)."),
                    @Param(name = "pattern", doc = "A list of 1-3 equal-length rows, e.g. `[\"RRR\", \"R R\", \"RRR\"]`."),
                    @Param(name = "key", doc = "A dict of single-character -> item/`#tag` id."),
                    @Param(name = "count", named = true, positional = false, defaultValue = "1",
                            doc = "How many result items to craft."),
            })
    public void shaped(Object result, Object pattern, Object key, StarlarkInt count) throws EvalException {
        List<String> rows = strings("shaped() pattern", pattern);
        if (rows.isEmpty() || rows.size() > 3) {
            throw error("recipes.shaped() pattern must have 1 to 3 rows");
        }
        int width = rows.get(0).length();
        for (String row : rows) {
            if (row.length() != width) {
                throw error("recipes.shaped() pattern rows must all be the same length");
            }
            if (width < 1 || width > 3) {
                throw error("recipes.shaped() pattern rows must be 1 to 3 characters wide");
            }
        }

        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");
        JsonArray patternArray = new JsonArray();
        rows.forEach(patternArray::add);
        recipe.add("pattern", patternArray);

        JsonObject keyObject = new JsonObject();
        for (Map.Entry<?, ?> entry : dict("shaped() key", key).entrySet()) {
            if (!(entry.getKey() instanceof String symbol) || symbol.length() != 1) {
                throw error("recipes.shaped() key names must be single characters");
            }
            if (symbol.equals(" ")) {
                throw error("recipes.shaped() cannot map the space character (it means an empty slot)");
            }
            keyObject.add(symbol, ingredient(entry.getValue()));
        }
        recipe.add("key", keyObject);
        recipe.add("result", result(result, count.toIntUnchecked()));
        record("shaped", recipe);
    }

    @StarlarkMethod(
            name = "shapeless",
            doc = "Adds a shapeless crafting recipe from 1-9 ingredients (items or `#tag`s).",
            parameters = {
                    @Param(name = "result", doc = "The output item id."),
                    @Param(name = "ingredients", doc = "A list of 1-9 item/`#tag` ids."),
                    @Param(name = "count", named = true, positional = false, defaultValue = "1",
                            doc = "How many result items to craft."),
            })
    public void shapeless(Object result, Object ingredients, StarlarkInt count) throws EvalException {
        if (!(ingredients instanceof Sequence<?> sequence)) {
            throw error("recipes.shapeless() ingredients must be a list");
        }
        JsonArray array = new JsonArray();
        for (Object element : sequence) {
            array.add(ingredient(element));
        }
        if (array.isEmpty() || array.size() > 9) {
            throw error("recipes.shapeless() needs 1 to 9 ingredients");
        }
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");
        recipe.add("ingredients", array);
        recipe.add("result", result(result, count.toIntUnchecked()));
        record("shapeless", recipe);
    }

    @StarlarkMethod(
            name = "smelting",
            doc = "Adds a furnace smelting recipe.",
            parameters = {
                    @Param(name = "result", doc = "The output item id."),
                    @Param(name = "ingredient", doc = "The input item or `#tag` id."),
                    @Param(name = "experience", named = true, positional = false, defaultValue = "0.0",
                            doc = "Experience granted."),
                    @Param(name = "cooking_time", named = true, positional = false, defaultValue = "200",
                            doc = "Cooking time in ticks."),
            })
    public void smelting(Object result, Object ingredient, Object experience, StarlarkInt cookingTime)
            throws EvalException {
        cooking("smelting", "minecraft:smelting", result, ingredient, experience, cookingTime);
    }

    @StarlarkMethod(
            name = "blasting",
            doc = "Adds a blast furnace recipe (usually half the smelting time).",
            parameters = {
                    @Param(name = "result", doc = "The output item id."),
                    @Param(name = "ingredient", doc = "The input item or `#tag` id."),
                    @Param(name = "experience", named = true, positional = false, defaultValue = "0.0",
                            doc = "Experience granted."),
                    @Param(name = "cooking_time", named = true, positional = false, defaultValue = "100",
                            doc = "Cooking time in ticks."),
            })
    public void blasting(Object result, Object ingredient, Object experience, StarlarkInt cookingTime)
            throws EvalException {
        cooking("blasting", "minecraft:blasting", result, ingredient, experience, cookingTime);
    }

    @StarlarkMethod(
            name = "smoking",
            doc = "Adds a smoker recipe (for food).",
            parameters = {
                    @Param(name = "result", doc = "The output item id."),
                    @Param(name = "ingredient", doc = "The input item or `#tag` id."),
                    @Param(name = "experience", named = true, positional = false, defaultValue = "0.0",
                            doc = "Experience granted."),
                    @Param(name = "cooking_time", named = true, positional = false, defaultValue = "100",
                            doc = "Cooking time in ticks."),
            })
    public void smoking(Object result, Object ingredient, Object experience, StarlarkInt cookingTime)
            throws EvalException {
        cooking("smoking", "minecraft:smoking", result, ingredient, experience, cookingTime);
    }

    private void cooking(String name, String type, Object result, Object ingredient, Object experience,
                         StarlarkInt cookingTime) throws EvalException {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);
        recipe.add("ingredient", ingredient(ingredient));
        recipe.addProperty("experience", (float) toDouble(experience));
        recipe.addProperty("cookingtime", cookingTime.toIntUnchecked());
        JsonObject resultObject = new JsonObject();
        resultObject.addProperty("id", idOf(result));
        recipe.add("result", resultObject);
        record(name, recipe);
    }

    @StarlarkMethod(
            name = "campfire",
            doc = "Adds a campfire cooking recipe (the slow, food-oriented cook over a campfire).",
            parameters = {
                    @Param(name = "result", doc = "The output item id."),
                    @Param(name = "ingredient", doc = "The input item or `#tag` id."),
                    @Param(name = "experience", named = true, positional = false, defaultValue = "0.0",
                            doc = "Experience granted."),
                    @Param(name = "cooking_time", named = true, positional = false, defaultValue = "100",
                            doc = "Cooking time in ticks."),
            })
    public void campfire(Object result, Object ingredient, Object experience, StarlarkInt cookingTime)
            throws EvalException {
        cooking("campfire", "minecraft:campfire_cooking", result, ingredient, experience, cookingTime);
    }

    @StarlarkMethod(
            name = "stonecutting",
            doc = "Adds a stonecutter recipe: one input turns into `count` copies of the result.",
            parameters = {
                    @Param(name = "result", doc = "The output item id."),
                    @Param(name = "ingredient", doc = "The input item or `#tag` id."),
                    @Param(name = "count", named = true, positional = false, defaultValue = "1",
                            doc = "How many result items are produced."),
            })
    public void stonecutting(Object result, Object ingredient, StarlarkInt count) throws EvalException {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:stonecutting");
        recipe.add("ingredient", ingredient(ingredient));
        recipe.add("result", result(result, count.toIntUnchecked()));
        record("stonecutting", recipe);
    }

    @StarlarkMethod(
            name = "smithing",
            doc = "Adds a smithing transform recipe (the smithing table upgrade, e.g. diamond -> netherite). "
                    + "`template`, `base`, and `addition` are each an item or `#tag`.",
            parameters = {
                    @Param(name = "result", doc = "The output item id."),
                    @Param(name = "template", doc = "The smithing template item/`#tag` id (the top-left slot)."),
                    @Param(name = "base", doc = "The base item/`#tag` id being upgraded."),
                    @Param(name = "addition", doc = "The addition item/`#tag` id (the material added)."),
            })
    public void smithing(Object result, Object template, Object base, Object addition) throws EvalException {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:smithing_transform");
        recipe.add("template", ingredient(template));
        recipe.add("base", ingredient(base));
        recipe.add("addition", ingredient(addition));
        JsonObject resultObject = new JsonObject();
        resultObject.addProperty("id", idOf(result));
        recipe.add("result", resultObject);
        record("smithing", recipe);
    }

    @StarlarkMethod(
            name = "remove",
            doc = "Removes existing recipes that match a filter dict. Give any subset of: `id` (an exact "
                    + "recipe id, bare defaults to minecraft:), `mod` (a namespace), `type` (a recipe type "
                    + "id like \"minecraft:crafting_shaped\"), `input` (an ingredient item or `#tag` id the "
                    + "recipe uses), or `output` (the result item id). All given keys must match. To replace "
                    + "a recipe, call remove(...) then add a new one with the same output.",
            parameters = {
                    @Param(name = "filter", doc = "A dict with any of `id`, `mod`, `type`, `input`, `output`."),
            })
    public void remove(Object filter) throws EvalException {
        Dict<?, ?> dict = dict("remove() filter", filter);
        String id = null;
        String mod = null;
        String type = null;
        String input = null;
        String output = null;
        for (Map.Entry<?, ?> entry : dict.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw error("recipes.remove() filter keys must be strings");
            }
            String value = filterString(key, entry.getValue());
            switch (key) {
                case "id" -> id = resolveId(value);
                case "mod" -> mod = namespace(value);
                case "type" -> type = resolveId(value);
                case "input" -> input = value.startsWith("#")
                        ? resolveId(value.substring(1))
                        : resolveId(value);
                case "output" -> output = resolveId(value);
                default -> throw error("recipes.remove() unknown filter key '" + key
                        + "'; use id, mod, type, input, or output");
            }
        }
        if (id == null && mod == null && type == null && input == null && output == null) {
            throw error("recipes.remove() needs at least one of id, mod, type, input, output");
        }
        removals.add(new RemovalSpec(id, mod, type, input, output));
    }

    // --- helpers ---

    private static String filterString(String key, Object value) throws EvalException {
        if (value instanceof Ref ref) {
            return ref.id();
        }
        if (value instanceof String string) {
            return string;
        }
        throw error("recipes.remove() filter '" + key + "' must be an id string or handle");
    }

    /** Validates a bare namespace (for the `mod` filter). */
    private static String namespace(String raw) throws EvalException {
        String value = raw.trim();
        if (!NAMESPACE.matcher(value).matches()) {
            throw error("invalid namespace '" + raw + "'");
        }
        return value;
    }

    private void record(String type, JsonObject recipe) {
        recipes.add(new RecipeSpec(type + "/" + (counter++), GSON.toJson(recipe)));
    }

    private static JsonObject ingredient(Object raw) throws EvalException {
        JsonObject object = new JsonObject();
        if (raw instanceof Ref ref) {
            object.addProperty("item", ref.id());
            return object;
        }
        if (!(raw instanceof String string)) {
            throw error("recipe ingredient must be an item id, #tag, or a handle");
        }
        String value = string.trim();
        if (value.startsWith("#")) {
            object.addProperty("tag", resolveId(value.substring(1)));
        } else {
            object.addProperty("item", resolveId(value));
        }
        return object;
    }

    private static JsonObject result(Object id, int count) throws EvalException {
        JsonObject object = new JsonObject();
        object.addProperty("id", idOf(id));
        object.addProperty("count", count);
        return object;
    }

    /** The full id of an item reference: a handle's id, or a resolved id string. */
    private static String idOf(Object value) throws EvalException {
        if (value instanceof Ref ref) {
            return ref.id();
        }
        if (value instanceof String string) {
            return resolveId(string);
        }
        throw error("expected an item id or a handle");
    }

    /** Resolves a resource id; a bare path (no namespace) defaults to {@code minecraft:}. */
    private static String resolveId(String raw) throws EvalException {
        String value = raw.trim();
        int colon = value.indexOf(':');
        String namespace = colon >= 0 ? value.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw error("invalid id '" + raw + "'");
        }
        return namespace + ":" + path;
    }

    private static List<String> strings(String what, Object sequence) throws EvalException {
        if (!(sequence instanceof Sequence<?> seq)) {
            throw error("recipes." + what + " must be a list");
        }
        List<String> out = new ArrayList<>();
        for (Object element : seq) {
            if (!(element instanceof String string)) {
                throw error("recipes." + what + " must be a list of strings");
            }
            out.add(string);
        }
        return out;
    }

    private static Dict<?, ?> dict(String what, Object value) throws EvalException {
        if (!(value instanceof Dict<?, ?> dict)) {
            throw error("recipes." + what + " must be a dict");
        }
        return dict;
    }

    private static double toDouble(Object value) throws EvalException {
        if (value instanceof StarlarkInt i) {
            return i.toIntUnchecked();
        }
        if (value instanceof StarlarkFloat f) {
            return f.toDouble();
        }
        throw error("expected a number, got " + value);
    }

    private static EvalException error(String message) {
        return new EvalException(message);
    }

    /** The recipes declared so far. */
    public List<RecipeSpec> recipes() {
        return List.copyOf(recipes);
    }

    /** The recipe-removal filters declared so far. */
    public List<RemovalSpec> removals() {
        return List.copyOf(removals);
    }
}
