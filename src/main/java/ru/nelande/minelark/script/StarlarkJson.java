package ru.nelande.minelark.script;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts a Starlark value (dict / list / string / number / bool / None) into JSON. Used by the
 * {@code datapack} namespace so scripts can emit arbitrary datapack files. Free of Minecraft types.
 */
public final class StarlarkJson {
    private static final Gson GSON = new Gson();

    private StarlarkJson() {
    }

    /** Serialises a Starlark value to a JSON string. */
    public static String toJsonString(Object value) throws EvalException {
        return GSON.toJson(toJson(value));
    }

    /** Converts a Starlark value to a Gson {@link JsonElement}. */
    public static JsonElement toJson(Object value) throws EvalException {
        if (value == Starlark.NONE) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof String s) {
            return new JsonPrimitive(s);
        }
        if (value instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (value instanceof StarlarkInt i) {
            return new JsonPrimitive(i.toNumber());
        }
        if (value instanceof StarlarkFloat f) {
            return new JsonPrimitive(f.toDouble());
        }
        if (value instanceof Dict<?, ?> dict) {
            JsonObject object = new JsonObject();
            for (var entry : dict.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new EvalException("JSON object keys must be strings, got a "
                            + Starlark.type(entry.getKey()));
                }
                object.add(key, toJson(entry.getValue()));
            }
            return object;
        }
        if (value instanceof Sequence<?> sequence) {
            JsonArray array = new JsonArray();
            for (Object element : sequence) {
                array.add(toJson(element));
            }
            return array;
        }
        throw new EvalException("cannot convert a " + Starlark.type(value) + " to JSON; "
                + "use dicts, lists, strings, numbers, booleans, or None");
    }

    /** Parses a JSON string back into a Starlark value (the inverse of {@link #toJsonString}). */
    public static Object fromJsonString(String json) {
        return fromJson(com.google.gson.JsonParser.parseString(json));
    }

    /** Converts a Gson {@link JsonElement} to an (immutable) Starlark value. */
    public static Object fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Starlark.NONE;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (var entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), fromJson(entry.getValue()));
            }
            return Dict.immutableCopyOf(map);
        }
        if (element.isJsonArray()) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                list.add(fromJson(child));
            }
            return StarlarkList.immutableCopyOf(list);
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        // A number: keep integers as ints (no decimal point / exponent), else a float.
        String text = primitive.getAsString();
        if (text.indexOf('.') < 0 && text.indexOf('e') < 0 && text.indexOf('E') < 0) {
            return StarlarkInt.of(new BigInteger(text));
        }
        return StarlarkFloat.of(primitive.getAsDouble());
    }
}
