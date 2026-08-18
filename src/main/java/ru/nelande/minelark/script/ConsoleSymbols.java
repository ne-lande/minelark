package ru.nelande.minelark.script;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds an autocomplete manifest for the console from the live predeclared globals, by reflecting the
 * same {@link StarlarkMethod} / {@link Param} annotations that drive the docs - so completion can
 * never drift from the API. MC-agnostic; the console server serves the JSON and the editor consumes it.
 *
 * <p>Shape: <code>{"globals": [name, ...], "members": {namespace: [{"name","sig"}, ...]}}</code>.
 * {@code globals} is every top-level name (namespaces + builtins); {@code members} lists the methods
 * of the namespace objects that carry {@code @StarlarkMethod}s.
 */
public final class ConsoleSymbols {
    private static final Gson GSON = new Gson();

    private ConsoleSymbols() {
    }

    /** Reflects {@code predeclared} (name -> value) into the autocomplete manifest JSON. */
    public static String toJson(Map<String, Object> predeclared) {
        JsonArray globals = new JsonArray();
        new TreeSet<>(predeclared.keySet()).forEach(globals::add);

        JsonObject members = new JsonObject();
        for (Map.Entry<String, Object> entry : predeclared.entrySet()) {
            JsonArray methods = membersOf(entry.getValue());
            if (!methods.isEmpty()) {
                members.add(entry.getKey(), methods);
            }
        }

        JsonObject root = new JsonObject();
        root.add("globals", globals);
        root.add("members", members);
        return GSON.toJson(root);
    }

    /** The {@code @StarlarkMethod} members of a namespace value's class, as {@code {name, sig}} entries. */
    private static JsonArray membersOf(Object value) {
        List<JsonObject> found = new ArrayList<>();
        TreeSet<String> seen = new TreeSet<>();
        for (Method method : value.getClass().getMethods()) {
            StarlarkMethod annotation = method.getAnnotation(StarlarkMethod.class);
            if (annotation == null || !seen.add(annotation.name())) {
                continue;
            }
            JsonObject member = new JsonObject();
            member.addProperty("name", annotation.name());
            member.addProperty("sig", signature(annotation));
            found.add(member);
        }
        found.sort(Comparator.comparing(member -> member.get("name").getAsString()));
        JsonArray array = new JsonArray();
        found.forEach(array::add);
        return array;
    }

    /** A display signature, e.g. {@code get(key, default=None)} - or just the name for a struct field. */
    private static String signature(StarlarkMethod method) {
        if (method.structField()) {
            return method.name();
        }
        StringBuilder builder = new StringBuilder(method.name()).append('(');
        Param[] params = method.parameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(params[i].name());
            if (!params[i].defaultValue().isEmpty()) {
                builder.append('=').append(params[i].defaultValue());
            }
        }
        return builder.append(')').toString();
    }
}
