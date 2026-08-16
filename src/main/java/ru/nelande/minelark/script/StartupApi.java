package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The global functions exposed to <b>startup</b> scripts (the phase that runs before the item
 * registry freezes). Methods annotated with {@link StarlarkMethod} become top-level Starlark
 * builtins; each call records a declaration that the Minecraft layer applies afterwards.
 *
 * <p>Declarative by design - this plays to Starlark's strengths (see the mod design notes):
 * <pre>{@code
 * item("ruby", max_stack_size = 16)
 * item("sapphire")
 * }</pre>
 */
public final class StartupApi implements StarlarkValue {
    /** Valid characters for a Minecraft resource path (the item id, minus the namespace). */
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern VALID_TAG_PATH = Pattern.compile("[a-z0-9_./-]+");

    private final List<ItemSpec> items = new ArrayList<>();
    private final List<BlockSpec> blocks = new ArrayList<>();

    @StarlarkMethod(
            name = "item",
            doc = """
                  Registers a new item under the `minelark:` namespace and adds it to the \
                  Ingredients creative tab.

                  Example:
                  ```python
                  item("ruby")
                  item("sapphire", max_stack_size = 16)
                  item("magic_wand", max_stack_size = 1)
                  ```

                  Example:
                  ```python
                  item("ruby")
                  item("ruby_sword", max_damage = 250, rarity = "rare")
                  item("phoenix_feather", fireproof = True, rarity = "epic")
                  ```

                  The item's translation key is `item.minelark.<id>`. Models and textures are not \
                  generated yet, so items appear as the missing-texture placeholder. Invalid input \
                  (a malformed id, a stack size outside 1-99, or an unknown rarity) is reported as a \
                  script error and skipped.
                  """,
            parameters = {
                    @Param(
                            name = "id",
                            doc = "The item path (no namespace); registered as `minelark:<id>`. Must match `[a-z0-9_.-]`."),
                    @Param(
                            name = "max_stack_size",
                            named = true,
                            positional = false,
                            defaultValue = "64",
                            doc = "Maximum stack size, from 1 to 99. Ignored when `max_damage` is set."),
                    @Param(
                            name = "max_damage",
                            named = true,
                            positional = false,
                            defaultValue = "0",
                            doc = "Durability. When above 0 the item becomes damageable (and unstackable); 0 means not damageable."),
                    @Param(
                            name = "rarity",
                            named = true,
                            positional = false,
                            defaultValue = "\"common\"",
                            doc = "Name colour tier: `common`, `uncommon`, `rare`, or `epic`."),
                    @Param(
                            name = "fireproof",
                            named = true,
                            positional = false,
                            defaultValue = "False",
                            doc = "If true, the item is not destroyed by fire or lava."),
                    @Param(
                            name = "nutrition",
                            named = true,
                            positional = false,
                            defaultValue = "0",
                            doc = "Hunger points restored when eaten. Above 0 makes the item edible."),
                    @Param(
                            name = "saturation",
                            named = true,
                            positional = false,
                            defaultValue = "0.0",
                            doc = "Food saturation modifier (only used when `nutrition` is above 0)."),
                    @Param(
                            name = "burn_time",
                            named = true,
                            positional = false,
                            defaultValue = "0",
                            doc = "Furnace burn time in ticks. Above 0 registers the item as fuel (coal is 1600)."),
                    @Param(
                            name = "display_name",
                            named = true,
                            positional = false,
                            defaultValue = "\"\"",
                            doc = "The item's shown name. Empty uses the default `item.minelark.<id>` translation."),
                    @Param(
                            name = "tags",
                            named = true,
                            positional = false,
                            defaultValue = "[]",
                            doc = "Item tags to join, e.g. `[\"c:gems\"]`. A name without a namespace uses `c:` (conventional)."),
            })
    public Ref item(String id, StarlarkInt maxStackSize, StarlarkInt maxDamage, String rarity, boolean fireproof,
                    StarlarkInt nutrition, Object saturation, StarlarkInt burnTime, String displayName, Object tags)
            throws EvalException {
        String path = validatePath("item", id);
        int stack = maxStackSize.toIntUnchecked();
        if (stack < 1 || stack > 99) {
            throw Starlark_error("item() max_stack_size must be between 1 and 99, got " + stack);
        }
        int damage = maxDamage.toIntUnchecked();
        if (damage < 0) {
            throw Starlark_error("item() max_damage must not be negative, got " + damage);
        }
        Rarity parsedRarity;
        try {
            parsedRarity = Rarity.valueOf(rarity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw Starlark_error("item() rarity '" + rarity + "' is invalid; use common, uncommon, rare, or epic");
        }
        int nut = nutrition.toIntUnchecked();
        if (nut < 0) {
            throw Starlark_error("item() nutrition must not be negative, got " + nut);
        }
        double sat = toDouble(saturation);
        if (sat < 0) {
            throw Starlark_error("item() saturation must not be negative, got " + sat);
        }
        int burn = burnTime.toIntUnchecked();
        if (burn < 0) {
            throw Starlark_error("item() burn_time must not be negative, got " + burn);
        }
        items.add(new ItemSpec(path, stack, damage, parsedRarity, fireproof, nut, sat, burn,
                displayName.trim(), resolveTags("item", tags)));
        return new Ref("minelark:" + path);
    }

    @StarlarkMethod(
            name = "block",
            doc = """
                  Registers a new block (and a matching block item) under the `minelark:` namespace,
                  adding the item to the Building Blocks creative tab.

                  Example:
                  ```python
                  block("marble", hardness = 1.5, resistance = 6.0)
                  block("glow_crystal", luminance = 15, requires_tool = True)
                  ```

                  Models, textures, and loot tables are not generated yet, so the block shows the
                  missing-texture placeholder and drops nothing. Invalid input is reported as a
                  script error and skipped.
                  """,
            parameters = {
                    @Param(
                            name = "id",
                            doc = "The block path (no namespace); registered as `minelark:<id>`. Must match `[a-z0-9_.-]`."),
                    @Param(
                            name = "hardness",
                            named = true,
                            positional = false,
                            defaultValue = "1.0",
                            doc = "Mining time factor (vanilla stone is 1.5)."),
                    @Param(
                            name = "resistance",
                            named = true,
                            positional = false,
                            defaultValue = "1.0",
                            doc = "Blast resistance (vanilla stone is 6.0)."),
                    @Param(
                            name = "luminance",
                            named = true,
                            positional = false,
                            defaultValue = "0",
                            doc = "Emitted light level, from 0 to 15."),
                    @Param(
                            name = "requires_tool",
                            named = true,
                            positional = false,
                            defaultValue = "False",
                            doc = "If true, the block only drops when broken with the correct tool."),
                    @Param(
                            name = "display_name",
                            named = true,
                            positional = false,
                            defaultValue = "\"\"",
                            doc = "The block's shown name. Empty uses the default `block.minelark.<id>` translation."),
                    @Param(
                            name = "tags",
                            named = true,
                            positional = false,
                            defaultValue = "[]",
                            doc = "Block tags to join, e.g. `[\"minecraft:mineable/pickaxe\"]`. A name without a namespace uses `c:`."),
                    @Param(
                            name = "drops",
                            named = true,
                            positional = false,
                            defaultValue = "\"\"",
                            doc = "What the block drops when broken: empty = itself, `\"none\"` = nothing, or an item id."),
            })
    public Ref block(String id, Object hardness, Object resistance, StarlarkInt luminance, boolean requiresTool,
                     String displayName, Object tags, Object drops) throws EvalException {
        String path = validatePath("block", id);
        double hard = toDouble(hardness);
        if (hard < 0) {
            throw Starlark_error("block() hardness must not be negative, got " + hard);
        }
        double res = toDouble(resistance);
        if (res < 0) {
            throw Starlark_error("block() resistance must not be negative, got " + res);
        }
        int lum = luminance.toIntUnchecked();
        if (lum < 0 || lum > 15) {
            throw Starlark_error("block() luminance must be between 0 and 15, got " + lum);
        }
        blocks.add(new BlockSpec(path, hard, res, lum, requiresTool, displayName.trim(),
                resolveTags("block", tags), resolveDrops(drops)));
        return new Ref("minelark:" + path);
    }

    /** Normalises the block {@code drops} option: "" (self), "none", or a resolved item id. */
    private static String resolveDrops(Object drops) throws EvalException {
        if (drops instanceof Ref ref) {
            return ref.id();
        }
        if (!(drops instanceof String string)) {
            throw Starlark_error("block() drops must be an item id or a handle");
        }
        String value = string.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("self")) {
            return "";
        }
        if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("nothing")) {
            return "none";
        }
        int colon = value.indexOf(':');
        String namespace = colon >= 0 ? value.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        if (!VALID_NAMESPACE.matcher(namespace).matches() || !VALID_ID.matcher(path).matches()) {
            throw Starlark_error("block() drops '" + drops + "' is not a valid item id");
        }
        return namespace + ":" + path;
    }

    /** Coerces a Starlark list of strings into resolved, validated tag ids ({@code namespace:path}). */
    private static List<String> resolveTags(String function, Object tags) throws EvalException {
        if (!(tags instanceof Sequence<?> sequence)) {
            throw Starlark_error(function + "() tags must be a list of strings");
        }
        List<String> resolved = new ArrayList<>();
        for (Object element : sequence) {
            if (!(element instanceof String raw)) {
                throw Starlark_error(function + "() tags must be a list of strings, got a " + Starlark.type(element));
            }
            resolved.add(resolveTag(function, raw));
        }
        return List.copyOf(resolved);
    }

    /** Resolves a single tag; a bare name (no namespace) uses the conventional {@code c:} namespace. */
    private static String resolveTag(String function, String raw) throws EvalException {
        String trimmed = raw.trim();
        int colon = trimmed.indexOf(':');
        String namespace = colon >= 0 ? trimmed.substring(0, colon) : "c";
        String path = colon >= 0 ? trimmed.substring(colon + 1) : trimmed;
        if (!VALID_NAMESPACE.matcher(namespace).matches() || !VALID_TAG_PATH.matcher(path).matches()) {
            throw Starlark_error(function + "() tag '" + raw + "' is invalid");
        }
        return namespace + ":" + path;
    }

    /** Validates and trims a resource path, throwing a friendly script error otherwise. */
    private static String validatePath(String function, String id) throws EvalException {
        String path = id.trim();
        if (path.isEmpty()) {
            throw Starlark_error(function + "() id must not be empty");
        }
        if (!VALID_ID.matcher(path).matches()) {
            throw Starlark_error(function + "() id '" + path + "' is invalid; use only [a-z0-9_.-]");
        }
        return path;
    }

    /** Accepts either a Starlark int or float for numeric options like saturation. */
    private static double toDouble(Object value) throws EvalException {
        if (value instanceof StarlarkInt i) {
            return i.toIntUnchecked();
        }
        if (value instanceof StarlarkFloat f) {
            return f.toDouble();
        }
        throw Starlark_error("expected a number, got " + value);
    }

    /** Small helper to keep the throw sites terse. */
    private static EvalException Starlark_error(String message) {
        return new EvalException(message);
    }

    /** The items declared so far, in declaration order. */
    public List<ItemSpec> items() {
        return List.copyOf(items);
    }

    /** The blocks declared so far, in declaration order. */
    public List<BlockSpec> blocks() {
        return List.copyOf(blocks);
    }
}
