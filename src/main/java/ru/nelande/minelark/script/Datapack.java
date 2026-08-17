package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code datapack} namespace for <b>server</b> scripts: a generic escape hatch for writing raw
 * JSON files into Minelark's generated data pack - for anything Minelark doesn't model directly
 * (advancements, dimensions, worldgen, custom data, ...). Files are regenerated on {@code /minelark
 * reload}. Free of Minecraft types.
 */
public final class Datapack implements StarlarkValue {
    // A safe relative path: dot-separated-ish segments, no traversal, no absolute paths.
    private static final Pattern SAFE_PATH = Pattern.compile("[a-zA-Z0-9_./-]+");

    private final List<DatapackJsonSpec> jsonFiles = new ArrayList<>();

    @StarlarkMethod(
            name = "json",
            doc = "Writes a JSON file into the generated data pack, under `data/`. Give a resource-style "
                    + "path (no `data/` prefix, no extension), e.g. `\"minelark/advancement/root\"`, and a "
                    + "value built from dicts, lists, strings, numbers, booleans, and `None`.",
            parameters = {
                    @Param(name = "path", doc = "The file path under `data/`, e.g. `\"minelark/predicate/on_fire\"`."),
                    @Param(name = "value", doc = "The JSON content as a dict/list/etc."),
            })
    public void json(String path, Object value) throws EvalException {
        String clean = path.trim();
        if (clean.isEmpty() || clean.startsWith("/") || clean.contains("..") || !SAFE_PATH.matcher(clean).matches()) {
            throw new EvalException("datapack.json() path '" + path + "' is invalid; use a safe relative "
                    + "path like \"minelark/advancement/root\"");
        }
        if (clean.endsWith(".json")) {
            clean = clean.substring(0, clean.length() - ".json".length());
        }
        String full = "data/" + clean + ".json";
        jsonFiles.add(new DatapackJsonSpec(full, StarlarkJson.toJsonString(value)));
    }

    /** The JSON files declared so far. */
    public List<DatapackJsonSpec> jsonFiles() {
        return List.copyOf(jsonFiles);
    }
}
