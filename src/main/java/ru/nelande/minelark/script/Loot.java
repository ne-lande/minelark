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
import java.util.regex.Pattern;

/**
 * The {@code loot} namespace for <b>server</b> scripts: replace an entity's drops, or inject extra
 * drops into an existing loot table (chests, entities, ...). Reloadable.
 *
 * <p>Each drop is an item id, or a dict {@code {"item": id, "count": N or [min, max], "chance": 0.0-1.0}}:
 * <pre>{@code
 * loot.entity_drops("minecraft:zombie", ["minelark:ruby", {"item": "minecraft:emerald", "count": [0, 2]}])
 * loot.inject("minecraft:chests/simple_dungeon", [{"item": "minelark:ruby", "chance": 0.25}])
 * }</pre>
 */
public final class Loot implements StarlarkValue {
    private static final Gson GSON = new Gson();
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private final List<EntityDropSpec> entityDrops = new ArrayList<>();
    private final List<LootInjectSpec> injects = new ArrayList<>();

    @StarlarkMethod(
            name = "entity_drops",
            doc = "Replaces what an entity drops when killed. Each drop is an item id or a "
                    + "`{\"item\", \"count\", \"chance\"}` dict. This overrides the entity's vanilla drops.",
            parameters = {
                    @Param(name = "entity", doc = "The entity type id, e.g. `\"minecraft:zombie\"`."),
                    @Param(name = "drops", doc = "A list of drops (item ids or dicts)."),
            })
    public void entityDrops(String entity, Object drops) throws EvalException {
        String entityId = resolveId(entity);
        List<LootDrop> parsed = parseDrops(drops);
        entityDrops.add(new EntityDropSpec(entityId, entityLootJson(parsed)));
    }

    @StarlarkMethod(
            name = "inject",
            doc = "Adds extra drops to an existing loot table (e.g. a chest) without replacing it. Each "
                    + "drop is an item id or a `{\"item\", \"count\", \"chance\"}` dict.",
            parameters = {
                    @Param(name = "table", doc = "The loot table id, e.g. `\"minecraft:chests/simple_dungeon\"`."),
                    @Param(name = "drops", doc = "A list of drops (item ids or dicts) to add as one pool."),
            })
    public void inject(String table, Object drops) throws EvalException {
        String tableId = resolveId(table);
        injects.add(new LootInjectSpec(tableId, parseDrops(drops)));
    }

    // --- parsing ---

    static List<LootDrop> parseDrops(Object drops) throws EvalException {
        if (!(drops instanceof Sequence<?> sequence)) {
            throw new EvalException("loot drops must be a list");
        }
        List<LootDrop> parsed = new ArrayList<>();
        for (Object element : sequence) {
            parsed.add(parseDrop(element));
        }
        if (parsed.isEmpty()) {
            throw new EvalException("loot drops must not be empty");
        }
        return parsed;
    }

    private static LootDrop parseDrop(Object element) throws EvalException {
        if (element instanceof String id) {
            return new LootDrop(resolveId(id), 1, 1, 1.0);
        }
        if (!(element instanceof Dict<?, ?> dict)) {
            throw new EvalException("each loot drop must be an item id or a dict");
        }
        Object itemValue = dict.get("item");
        if (!(itemValue instanceof String id)) {
            throw new EvalException("a loot drop dict needs a string \"item\"");
        }
        int min = 1;
        int max = 1;
        Object count = dict.get("count");
        if (count instanceof StarlarkInt n) {
            min = max = n.toIntUnchecked();
        } else if (count instanceof Sequence<?> range) {
            if (range.size() != 2 || !(range.get(0) instanceof StarlarkInt lo) || !(range.get(1) instanceof StarlarkInt hi)) {
                throw new EvalException("loot drop \"count\" range must be [min, max] integers");
            }
            min = lo.toIntUnchecked();
            max = hi.toIntUnchecked();
        } else if (count != null) {
            throw new EvalException("loot drop \"count\" must be an int or an [min, max] list");
        }
        if (min < 0 || max < min) {
            throw new EvalException("loot drop \"count\" must be non-negative with max >= min");
        }
        double chance = 1.0;
        Object chanceValue = dict.get("chance");
        if (chanceValue instanceof StarlarkFloat f) {
            chance = f.toDouble();
        } else if (chanceValue instanceof StarlarkInt i) {
            chance = i.toIntUnchecked();
        } else if (chanceValue != null) {
            throw new EvalException("loot drop \"chance\" must be a number");
        }
        if (chance <= 0 || chance > 1) {
            throw new EvalException("loot drop \"chance\" must be in (0, 1]");
        }
        return new LootDrop(resolveId(id), min, max, chance);
    }

    // --- entity loot-table JSON ---

    private static String entityLootJson(List<LootDrop> drops) {
        JsonArray entries = new JsonArray();
        for (LootDrop drop : drops) {
            entries.add(lootEntry(drop));
        }
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("entries", entries);
        JsonArray pools = new JsonArray();
        pools.add(pool);
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:entity");
        table.add("pools", pools);
        return GSON.toJson(table);
    }

    /** Builds one {@code minecraft:item} loot entry (with set_count / random_chance as needed). */
    static JsonObject lootEntry(LootDrop drop) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", drop.itemId());
        if (drop.min() != 1 || drop.max() != 1) {
            JsonObject setCount = new JsonObject();
            setCount.addProperty("function", "minecraft:set_count");
            if (drop.min() == drop.max()) {
                setCount.addProperty("count", drop.min());
            } else {
                JsonObject count = new JsonObject();
                count.addProperty("min", drop.min());
                count.addProperty("max", drop.max());
                setCount.add("count", count);
            }
            JsonArray functions = new JsonArray();
            functions.add(setCount);
            entry.add("functions", functions);
        }
        if (drop.chance() < 1.0) {
            JsonObject condition = new JsonObject();
            condition.addProperty("condition", "minecraft:random_chance");
            condition.addProperty("chance", drop.chance());
            JsonArray conditions = new JsonArray();
            conditions.add(condition);
            entry.add("conditions", conditions);
        }
        return entry;
    }

    private static String resolveId(String raw) throws EvalException {
        String value = raw.trim();
        int colon = value.indexOf(':');
        String namespace = colon >= 0 ? value.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw new EvalException("invalid id '" + raw + "'");
        }
        return namespace + ":" + path;
    }

    /** The entity-drop replacements declared so far. (Named distinctly from the {@code entity_drops}
     * builtin - a same-named Java accessor breaks @StarlarkMethod resolution.) */
    public List<EntityDropSpec> entityDropSpecs() {
        return List.copyOf(entityDrops);
    }

    /** The loot-table injections declared so far. */
    public List<LootInjectSpec> injectSpecs() {
        return List.copyOf(injects);
    }
}
