package ru.nelande.minelark.script;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The offer a server sends before pushing client scripts: the files it wants to run (each with a
 * SHA-256 so the client can verify integrity and cache by content), the capabilities it requests, and
 * a {@code bundleHash} fingerprinting the whole offer. The client compares the {@code bundleHash} to
 * decide whether anything changed (hot-push) and whether to re-ask for consent; it never runs a body
 * whose hash does not match its {@link Entry}.
 *
 * <p>Carries no script bodies - those are fetched on demand ({@code push_request} -> {@code push_deliver})
 * so a declining client is never sent code. MC-agnostic and unit-testable.
 */
public record PushManifest(String bundleHash, List<Entry> entries, Set<Capability> requested) {

    /** One pushable script: its path (relative to the push folder), content hash, and byte size. */
    public record Entry(String name, String sha256, long size) {
    }

    private static final Gson GSON = new Gson();

    public PushManifest {
        entries = List.copyOf(entries);
        requested = Set.copyOf(requested);
    }

    /** Builds a manifest, deriving the {@code bundleHash} from the (sorted) entries and capabilities. */
    public static PushManifest of(List<Entry> entries, Set<Capability> requested) {
        return new PushManifest(computeBundleHash(entries, requested), entries, requested);
    }

    /**
     * A stable fingerprint of the offer: SHA-256 over the entries (sorted by name, each as
     * {@code name:sha256:size}) followed by the requested capability tokens. Independent of ordering,
     * so it only changes when the set of files, their content, their sizes, or the capabilities do.
     */
    private static String computeBundleHash(List<Entry> entries, Set<Capability> requested) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(Entry::name));
        StringBuilder canonical = new StringBuilder();
        for (Entry entry : sorted) {
            canonical.append(entry.name()).append(':').append(entry.sha256())
                    .append(':').append(entry.size()).append('\n');
        }
        canonical.append("caps=");
        for (String token : Capability.tokens(requested)) {   // Capability.tokens is already in a fixed order
            canonical.append(token).append(',');
        }
        return Sha256.hex(canonical.toString());
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("bundle_hash", bundleHash);
        JsonArray caps = new JsonArray();
        Capability.tokens(requested).forEach(caps::add);
        root.add("capabilities", caps);
        JsonArray files = new JsonArray();
        for (Entry entry : entries) {
            JsonObject file = new JsonObject();
            file.addProperty("name", entry.name());
            file.addProperty("sha256", entry.sha256());
            file.addProperty("size", entry.size());
            files.add(file);
        }
        root.add("files", files);
        return root;
    }

    public String toJsonString() {
        return GSON.toJson(toJson());
    }

    /** Parses a manifest from its JSON form. Unknown capability tokens are dropped (forward-compatible). */
    public static PushManifest fromJsonString(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String bundleHash = root.has("bundle_hash") ? root.get("bundle_hash").getAsString() : "";
        List<String> capTokens = new ArrayList<>();
        if (root.has("capabilities")) {
            root.getAsJsonArray("capabilities").forEach(e -> capTokens.add(e.getAsString()));
        }
        List<Entry> entries = new ArrayList<>();
        if (root.has("files")) {
            for (var element : root.getAsJsonArray("files")) {
                JsonObject file = element.getAsJsonObject();
                entries.add(new Entry(
                        file.get("name").getAsString(),
                        file.get("sha256").getAsString(),
                        file.has("size") ? file.get("size").getAsLong() : 0L));
            }
        }
        return new PushManifest(bundleHash, entries, Capability.parse(capTokens));
    }
}
