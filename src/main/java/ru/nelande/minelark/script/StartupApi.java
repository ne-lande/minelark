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

    // Fixed (non-extensible) option sets: tool kinds map to item classes, armor slots to an enum.
    private static final java.util.Set<String> TOOL_TYPES = java.util.Set.of("pickaxe", "axe", "shovel", "hoe", "sword");
    private static final java.util.Set<String> ARMOR_SLOTS = java.util.Set.of("helmet", "chestplate", "leggings", "boots");

    /** Valid names for the extensible options (sound/tool_tier/shape/armor_material). */
    private final TypeCatalog catalog;

    private final List<ItemSpec> items = new ArrayList<>();
    private final List<BlockSpec> blocks = new ArrayList<>();
    private final List<FluidSpec> fluids = new ArrayList<>();

    public StartupApi(TypeCatalog catalog) {
        this.catalog = catalog;
    }

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

                  The item's translation key is `item.minelark.<id>`. Minelark generates the item's \
                  model; supply a texture at `minelark/assets/minelark/textures/item/<id>.png` (until \
                  then it shows the missing-texture placeholder). Invalid input (a malformed id, a \
                  stack size outside 1-99, or an unknown rarity) is reported as a script error and skipped.
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
                    @Param(
                            name = "tool_type",
                            named = true,
                            positional = false,
                            defaultValue = "\"\"",
                            doc = "Makes this a tool: `pickaxe`, `axe`, `shovel`, `hoe`, or `sword`. Requires `tool_tier`."),
                    @Param(
                            name = "tool_tier",
                            named = true,
                            positional = false,
                            defaultValue = "\"\"",
                            doc = "Tool material: `wood`, `stone`, `iron`, `gold`, `diamond`, or `netherite`. "
                                    + "Sets durability, mining level, and attack values."),
                    @Param(
                            name = "armor_slot",
                            named = true,
                            positional = false,
                            defaultValue = "\"\"",
                            doc = "Makes this armor: `helmet`, `chestplate`, `leggings`, or `boots`. Requires `armor_material`."),
                    @Param(
                            name = "armor_material",
                            named = true,
                            positional = false,
                            defaultValue = "\"\"",
                            doc = "Armor material: `leather`, `chainmail`, `iron`, `gold`, `diamond`, `netherite`, "
                                    + "`turtle`, or any mod's armor-material id as `namespace:id`."),
            })
    public Ref item(String id, StarlarkInt maxStackSize, StarlarkInt maxDamage, String rarity, boolean fireproof,
                    StarlarkInt nutrition, Object saturation, StarlarkInt burnTime, String displayName, Object tags,
                    String toolType, String toolTier, String armorSlot, String armorMaterial)
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
        String tType = validateChoice("item", "tool_type", toolType, TOOL_TYPES);
        String tTier = validateChoice("item", "tool_tier", toolTier, catalog.toolTiers());
        String aSlot = validateChoice("item", "armor_slot", armorSlot, ARMOR_SLOTS);
        String aMat = validateArmorMaterial(armorMaterial);
        if (tType.isEmpty() != tTier.isEmpty()) {
            throw Starlark_error("item() tool_type and tool_tier must be set together");
        }
        if (aSlot.isEmpty() != aMat.isEmpty()) {
            throw Starlark_error("item() armor_slot and armor_material must be set together");
        }
        if (!tType.isEmpty() && !aSlot.isEmpty()) {
            throw Starlark_error("item() cannot be both a tool and armor");
        }
        items.add(new ItemSpec(path, stack, damage, parsedRarity, fireproof, nut, sat, burn,
                displayName.trim(), resolveTags("item", tags), tType, tTier, aSlot, aMat));
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

                  Minelark generates the block's model, blockstate, and loot table; supply a texture
                  at `minelark/assets/minelark/textures/block/<id>.png` (until then it shows the
                  missing-texture placeholder). Invalid input is reported as a script error and skipped.
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
                    @Param(
                            name = "sound",
                            named = true,
                            positional = false,
                            defaultValue = "\"\"",
                            doc = "Break/step sound group: stone (default), wood, gravel, grass, metal, glass, "
                                    + "wool, sand, snow, ladder, anvil, slime, honey, bamboo, or nether."),
                    @Param(
                            name = "shape",
                            named = true,
                            positional = false,
                            defaultValue = "\"\"",
                            doc = "Block shape: empty = full cube (default), or `slab`, `stairs`, `fence`, `wall`."),
            })
    public Ref block(String id, Object hardness, Object resistance, StarlarkInt luminance, boolean requiresTool,
                     String displayName, Object tags, Object drops, String sound, String shape) throws EvalException {
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
        String soundGroup = validateChoice("block", "sound", sound, catalog.sounds());
        String blockShape = validateChoice("block", "shape", shape, catalog.shapes());
        blocks.add(new BlockSpec(path, hard, res, lum, requiresTool, displayName.trim(),
                resolveTags("block", tags), resolveDrops(drops), soundGroup, blockShape));
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

    /**
     * Validates {@code armor_material}: empty (none), a registered alias (e.g. {@code iron}), or - since
     * armor materials are registry-backed - any well-formed {@code namespace:id} (resolved by the adapter).
     */
    private String validateArmorMaterial(String raw) throws EvalException {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || catalog.armorMaterials().contains(value)) {
            return value;
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            String namespace = value.substring(0, colon);
            String path = value.substring(colon + 1);
            if (VALID_NAMESPACE.matcher(namespace).matches() && VALID_ID.matcher(path).matches()) {
                return value;  // a mod's namespace:id; existence is checked when it is registered
            }
        }
        throw Starlark_error("item() armor_material '" + raw + "' is invalid; use a known material ("
                + String.join(", ", new java.util.TreeSet<>(catalog.armorMaterials())) + ") or a namespace:id");
    }

    /** Validates an optional enum-like string option: empty is allowed (the default), else must be in {@code allowed}. */
    private static String validateChoice(String function, String option, String raw, java.util.Set<String> allowed)
            throws EvalException {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        if (!allowed.contains(value)) {
            throw Starlark_error(function + "() " + option + " '" + raw + "' is invalid; use one of "
                    + String.join(", ", new java.util.TreeSet<>(allowed)));
        }
        return value;
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

    @StarlarkMethod(
            name = "fluid",
            doc = """
                  Registers a custom fluid under the `minelark:` namespace: a still and flowing fluid, a
                  fluid block, and a filled bucket (`minelark:<id>_bucket`).

                  Example:
                  ```python
                  fluid("acid", luminance = 7, tint = "#66ff33")
                  ```

                  Fluids need a `<id>_still` and `<id>_flow` texture in the resource pack. Rendering can
                  only be seen in-game (not on a headless server). Invalid input is reported and skipped.
                  """,
            parameters = {
                    @Param(name = "id",
                            doc = "The fluid path (no namespace); registered as `minelark:<id>`. Must match `[a-z0-9_.-]`."),
                    @Param(name = "display_name", named = true, positional = false, defaultValue = "\"\"",
                            doc = "The bucket's shown name. Empty uses the default translation."),
                    @Param(name = "luminance", named = true, positional = false, defaultValue = "0",
                            doc = "Light the fluid emits, from 0 to 15."),
                    @Param(name = "tint", named = true, positional = false, defaultValue = "\"#ffffff\"",
                            doc = "Colour applied to the fluid textures, as `#rrggbb` (default white)."),
            })
    public Ref fluid(String id, String displayName, StarlarkInt luminance, String tint) throws EvalException {
        String path = validatePath("fluid", id);
        int lum = luminance.toIntUnchecked();
        if (lum < 0 || lum > 15) {
            throw Starlark_error("fluid() luminance must be between 0 and 15, got " + lum);
        }
        int color = parseColor(tint);
        fluids.add(new FluidSpec(path, displayName.trim(), lum, color));
        return new Ref("minelark:" + path);
    }

    /** Parses a {@code #rrggbb} colour into a packed RGB int. */
    private static int parseColor(String raw) throws EvalException {
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            throw Starlark_error("fluid() tint '" + raw + "' must be a #rrggbb colour");
        }
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            throw Starlark_error("fluid() tint '" + raw + "' must be a #rrggbb colour");
        }
    }

    /** The fluids declared so far, in declaration order. */
    public List<FluidSpec> fluids() {
        return List.copyOf(fluids);
    }
}
