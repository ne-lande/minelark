package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code tags} namespace for <b>server</b> scripts: add members to item / block / fluid / entity
 * tags. Unlike the {@code tags=} option on {@code item()}/{@code block()} (which tags the thing being
 * declared), this adds any ids - including vanilla or other mods' - to any tag, for any registry.
 * Written into the generated data pack (reloadable). Free of Minecraft types.
 *
 * <pre>{@code
 * tags.block("c:ores", ["minelark:ruby_ore", "minecraft:iron_ore"])
 * tags.fluid("minecraft:water", ["minelark:flowing_acid"])
 * tags.entity("c:bosses", ["minecraft:ender_dragon"])
 * }</pre>
 */
public final class Tags implements StarlarkValue {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private final List<TagSpec> tags = new ArrayList<>();

    @StarlarkMethod(
            name = "item",
            doc = "Adds items to an item tag. A bare tag name uses the conventional `c:` namespace.",
            parameters = {
                    @Param(name = "tag", doc = "The tag id, e.g. `\"c:gems\"` or `\"minecraft:planks\"`."),
                    @Param(name = "members", doc = "A list of item ids (or `#tag` to include another tag)."),
            })
    public void item(String tag, Object members) throws EvalException {
        add("item", tag, members);
    }

    @StarlarkMethod(
            name = "block",
            doc = "Adds blocks to a block tag. A bare tag name uses the conventional `c:` namespace.",
            parameters = {
                    @Param(name = "tag", doc = "The tag id, e.g. `\"minecraft:mineable/pickaxe\"`."),
                    @Param(name = "members", doc = "A list of block ids (or `#tag`)."),
            })
    public void block(String tag, Object members) throws EvalException {
        add("block", tag, members);
    }

    @StarlarkMethod(
            name = "fluid",
            doc = "Adds fluids to a fluid tag. A bare tag name uses the conventional `c:` namespace.",
            parameters = {
                    @Param(name = "tag", doc = "The tag id, e.g. `\"minecraft:water\"`."),
                    @Param(name = "members", doc = "A list of fluid ids (or `#tag`)."),
            })
    public void fluid(String tag, Object members) throws EvalException {
        add("fluid", tag, members);
    }

    @StarlarkMethod(
            name = "entity",
            doc = "Adds entity types to an entity-type tag. A bare tag name uses the conventional `c:` namespace.",
            parameters = {
                    @Param(name = "tag", doc = "The tag id, e.g. `\"c:bosses\"`."),
                    @Param(name = "members", doc = "A list of entity type ids (or `#tag`)."),
            })
    public void entity(String tag, Object members) throws EvalException {
        add("entity_type", tag, members);
    }

    private void add(String kind, String tag, Object members) throws EvalException {
        String tagId = resolveTag(tag);
        if (!(members instanceof Sequence<?> sequence)) {
            throw new EvalException("tags." + kindName(kind) + "() members must be a list");
        }
        List<String> resolved = new ArrayList<>();
        for (Object element : sequence) {
            if (!(element instanceof String member)) {
                throw new EvalException("tags." + kindName(kind) + "() members must be a list of strings");
            }
            resolved.add(resolveMember(member));
        }
        tags.add(new TagSpec(kind, tagId, resolved));
    }

    private static String kindName(String kind) {
        return kind.equals("entity_type") ? "entity" : kind;
    }

    /** Resolves a tag id; a leading `#` is optional and stripped; a bare name uses `c:`. */
    private static String resolveTag(String raw) throws EvalException {
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        int colon = value.indexOf(':');
        String namespace = colon >= 0 ? value.substring(0, colon) : "c";
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw new EvalException("invalid tag '" + raw + "'");
        }
        return namespace + ":" + path;
    }

    /** Resolves a tag member; `#tag` includes another tag, otherwise a bare id defaults to `minecraft:`. */
    private static String resolveMember(String raw) throws EvalException {
        String value = raw.trim();
        boolean include = value.startsWith("#");
        if (include) {
            value = value.substring(1);
        }
        int colon = value.indexOf(':');
        String namespace = colon >= 0 ? value.substring(0, colon) : (include ? "c" : "minecraft");
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw new EvalException("invalid tag member '" + raw + "'");
        }
        return (include ? "#" : "") + namespace + ":" + path;
    }

    /** The explicit tag declarations so far. */
    public List<TagSpec> tags() {
        return List.copyOf(tags);
    }
}
