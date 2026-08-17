package ru.nelande.minelark.script;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The {@code storage} namespace for <b>server</b> scripts: a small persistent key-value store that
 * survives {@code /minelark reload} and server restarts. Handy from event callbacks (a join counter,
 * a per-player flag, config a command writes). Values are any JSON-able Starlark value (dict, list,
 * string, number, bool, {@code None}).
 *
 * <p>Backed by a JSON file (or kept in memory when the file is {@code null}). Free of Minecraft types,
 * so it is unit-testable: every mutation is flushed to disk immediately, and a new {@code Storage}
 * over the same file sees the data.
 *
 * <p>The same class backs three scopes the adapter wires up: the install-global {@code storage}, the
 * per-world {@code world} (rebound to the world save via {@link #bindFile}), and per-player stores
 * handed out by {@link #player} (kept under a directory set by {@link #bindPlayerDir}).
 */
public final class Storage implements StarlarkValue {
    private static final Gson GSON = new Gson();

    /** Player ids used as file names; a canonical uuid matches, and path traversal cannot. */
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private Path file;
    private final Map<String, JsonElement> entries = new LinkedHashMap<>();

    /** Directory for {@link #player} stores; {@code null} until a world is loaded (per-player disabled). */
    private Path playerDir;
    /** Live per-player stores, so repeated {@code player(uuid)} calls share one object (and its writes). */
    private final Map<String, Storage> playerStores = new HashMap<>();

    /** Creates a store backed by {@code file} (loaded now if it exists), or in-memory if {@code null}. */
    public Storage(Path file) {
        this.file = file;
        load();
    }

    @StarlarkMethod(
            name = "set",
            doc = "Stores a value under a key (replacing any existing one) and saves to disk. The value "
                    + "may be a dict, list, string, number, bool, or `None`.",
            parameters = {
                    @Param(name = "key", doc = "The key to store under."),
                    @Param(name = "value", doc = "The value to store (must be JSON-able)."),
            })
    public void set(String key, Object value) throws EvalException {
        entries.put(key, StarlarkJson.toJson(value));
        save();
    }

    @StarlarkMethod(
            name = "get",
            doc = "Returns the value stored under a key, or `default` (itself defaulting to `None`) if absent.",
            parameters = {
                    @Param(name = "key", doc = "The key to look up."),
                    @Param(name = "default", named = true, defaultValue = "None",
                            doc = "What to return if the key is not present."),
            })
    public Object get(String key, Object defaultValue) {
        JsonElement value = entries.get(key);
        return value == null ? defaultValue : StarlarkJson.fromJson(value);
    }

    @StarlarkMethod(
            name = "has",
            doc = "Whether a value is stored under the key.",
            parameters = {@Param(name = "key", doc = "The key to check.")})
    public boolean has(String key) {
        return entries.containsKey(key);
    }

    @StarlarkMethod(
            name = "delete",
            doc = "Removes the value stored under a key (saving to disk). Returns whether one was removed.",
            parameters = {@Param(name = "key", doc = "The key to remove.")})
    public boolean delete(String key) {
        boolean removed = entries.remove(key) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    @StarlarkMethod(name = "keys", doc = "The keys currently stored, in insertion order.")
    public StarlarkList<String> keys() {
        return StarlarkList.immutableCopyOf(new ArrayList<>(entries.keySet()));
    }

    @StarlarkMethod(name = "clear", doc = "Removes everything from the store (saving to disk).")
    public void clear() {
        entries.clear();
        save();
    }

    @StarlarkMethod(
            name = "player",
            doc = "Returns a persistent per-player store for a player id (usually `ctx.player.uuid`). "
                    + "It is kept with the **world save**, so it is both per-world and per-player, and "
                    + "has the same `set`/`get`/`has`/`delete`/`keys`/`clear` methods as this one. Only "
                    + "available once a world is loaded, so call it from an event or command callback.",
            parameters = {@Param(name = "uuid",
                    doc = "The player's unique id, e.g. `ctx.player.uuid`.")})
    public Storage player(String uuid) throws EvalException {
        if (playerDir == null) {
            throw Starlark.errorf("per-player storage is only available once a world is loaded "
                    + "(call storage.player(...) from an event or command callback, not at the top level)");
        }
        if (!SAFE_ID.matcher(uuid).matches()) {
            throw Starlark.errorf("invalid player id '%s' (expected a uuid)", uuid);
        }
        return playerStores.computeIfAbsent(uuid, u -> new Storage(playerDir.resolve(u + ".json")));
    }

    // --- adapter wiring (not script-visible): rebind the backing file / player directory as a world
    //     loads and unloads. The one Storage object is reused across /minelark reload. ---

    /** (Re)binds this store to a file, loading its current contents (for {@code world} on world load). */
    public void bindFile(Path newFile) {
        this.file = newFile;
        entries.clear();
        load();
    }

    /** Points {@link #player} at a directory of per-player files (the world save). Null disables it. */
    public void bindPlayerDir(Path dir) {
        this.playerDir = dir;
        playerStores.clear();
    }

    /** Detaches from a world on server stop: drops the file binding, live data, and per-player stores. */
    public void unbindWorld() {
        this.file = null;
        entries.clear();
        this.playerDir = null;
        playerStores.clear();
    }

    // --- persistence (named distinctly from the builtins to avoid @StarlarkMethod name clashes) ---

    private void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            String text = Files.readString(file);
            if (text.isBlank()) {
                return;
            }
            JsonElement root = JsonParser.parseString(text);
            if (root.isJsonObject()) {
                for (var entry : root.getAsJsonObject().entrySet()) {
                    entries.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Minelark storage " + file, e);
        } catch (RuntimeException e) {
            // A corrupt file starts empty rather than crashing the whole script phase.
            entries.clear();
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        entries.forEach(root::add);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Minelark storage " + file, e);
        }
    }

    /** Package-visible for tests: the raw stored keys. */
    List<String> storedKeys() {
        return new ArrayList<>(entries.keySet());
    }
}
