package ru.nelande.minelark.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The client's editable security policy for server-pushed scripts, read from and written to
 * {@code <gamedir>/minelark/remote_policy.json}. The client - not the server - is authoritative: a
 * server may <i>request</i> capabilities, but only what this policy allows is ever {@link #granted}.
 *
 * <p>Secure by default: {@link #defaultAllow} starts at the visual-only {@link Capability#VISUAL_DEFAULTS}
 * (no {@code net}, no {@code chat}), and an unknown source is asked about rather than trusted. The
 * player can widen the allow-set, mark sources as always-allowed ({@code trusted_sources}) or never
 * ({@code blocked_sources}), and per-server accept/decline choices are remembered here. MC-agnostic and
 * unit-testable; every change is flushed to disk immediately (crash-safe), like {@link Storage}.
 */
public final class RemoteScriptPolicy {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String ACCEPTED = "accepted";
    private static final String DECLINED = "declined";

    /** What to do with an offer from a given source. */
    public enum Decision {
        /** Ask the player (unknown source, no remembered choice). */
        PROMPT,
        /** Proceed without asking (trusted source or a remembered accept). */
        ACCEPT,
        /** Ignore the offer (feature off, blocked source, or a remembered decline). */
        DECLINE
    }

    private Path file;   // nullable => in-memory (tests)
    private boolean enabled = true;
    private final Set<Capability> defaultAllow = EnumSet.copyOf(Capability.VISUAL_DEFAULTS);
    private final Set<String> trustedSources = new TreeSet<>();
    private final Set<String> blockedSources = new TreeSet<>();
    private final Map<String, String> decisions = new LinkedHashMap<>();

    private RemoteScriptPolicy(Path file) {
        this.file = file;
    }

    /** An in-memory policy with secure defaults (no file). For tests and transient use. */
    public static RemoteScriptPolicy inMemory() {
        return new RemoteScriptPolicy(null);
    }

    /** Loads the policy from {@code file}, writing a default (secure) file first if none exists. */
    public static RemoteScriptPolicy load(Path file) {
        RemoteScriptPolicy policy = new RemoteScriptPolicy(file);
        try {
            if (!Files.exists(file)) {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                policy.save();
                return policy;
            }
            String text = Files.readString(file);
            if (!text.isBlank()) {
                policy.readJson(JsonParser.parseString(text).getAsJsonObject());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Minelark remote policy " + file, e);
        } catch (RuntimeException malformed) {
            // A corrupt policy falls back to secure defaults rather than breaking the client.
            return new RemoteScriptPolicy(file);
        }
        return policy;
    }

    /** Decides what to do with an offer from {@code sourceKey} requesting {@code requested}. */
    public synchronized Decision decide(String sourceKey, Set<Capability> requested) {
        if (!enabled) {
            return Decision.DECLINE;
        }
        if (blockedSources.contains(sourceKey)) {
            return Decision.DECLINE;
        }
        if (trustedSources.contains(sourceKey)) {
            return Decision.ACCEPT;
        }
        String remembered = decisions.get(sourceKey);
        if (ACCEPTED.equals(remembered)) {
            return Decision.ACCEPT;
        }
        if (DECLINED.equals(remembered)) {
            return Decision.DECLINE;
        }
        return Decision.PROMPT;
    }

    /** The capabilities actually granted: what the server asked for, capped by what this policy allows. */
    public synchronized Set<Capability> granted(Set<Capability> requested) {
        Set<Capability> result = EnumSet.noneOf(Capability.class);
        for (Capability capability : requested) {
            if (defaultAllow.contains(capability)) {
                result.add(capability);
            }
        }
        return result;
    }

    /** Records the player's accept/decline choice for a source and persists it. */
    public synchronized void remember(String sourceKey, boolean accepted) {
        decisions.put(sourceKey, accepted ? ACCEPTED : DECLINED);
        save();
    }

    /** Marks a source always-allowed (and clears any remembered decline/block for it). */
    public synchronized void trust(String sourceKey) {
        trustedSources.add(sourceKey);
        blockedSources.remove(sourceKey);
        decisions.remove(sourceKey);
        save();
    }

    /** Marks a source never-allowed (and clears any trust/remembered accept for it). */
    public synchronized void block(String sourceKey) {
        blockedSources.add(sourceKey);
        trustedSources.remove(sourceKey);
        decisions.remove(sourceKey);
        save();
    }

    /** Clears any trust/block/remembered choice for a source (so it will be asked about again). */
    public synchronized void forget(String sourceKey) {
        trustedSources.remove(sourceKey);
        blockedSources.remove(sourceKey);
        decisions.remove(sourceKey);
        save();
    }

    public synchronized boolean enabled() {
        return enabled;
    }

    public synchronized Set<Capability> defaultAllow() {
        return EnumSet.copyOf(defaultAllow);
    }

    public synchronized Set<String> trustedSources() {
        return new TreeSet<>(trustedSources);
    }

    public synchronized Map<String, String> decisions() {
        return new LinkedHashMap<>(decisions);
    }

    private void readJson(JsonObject root) {
        if (root.has("enabled")) {
            enabled = root.get("enabled").getAsBoolean();
        }
        if (root.has("default_allow")) {
            defaultAllow.clear();
            for (var element : root.getAsJsonArray("default_allow")) {
                Capability.fromToken(element.getAsString()).ifPresent(defaultAllow::add);
            }
        }
        readStringArray(root, "trusted_sources", trustedSources);
        readStringArray(root, "blocked_sources", blockedSources);
        if (root.has("decisions")) {
            for (var entry : root.getAsJsonObject("decisions").entrySet()) {
                decisions.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    private static void readStringArray(JsonObject root, String key, Set<String> into) {
        if (root.has(key)) {
            into.clear();
            for (var element : root.getAsJsonArray(key)) {
                into.add(element.getAsString());
            }
        }
    }

    private synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file, GSON.toJson(toJson()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Minelark remote policy " + file, e);
        }
    }

    private JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        JsonArray allow = new JsonArray();
        Capability.tokens(defaultAllow).forEach(allow::add);
        root.add("default_allow", allow);
        JsonArray trusted = new JsonArray();
        trustedSources.forEach(trusted::add);
        root.add("trusted_sources", trusted);
        JsonArray blocked = new JsonArray();
        blockedSources.forEach(blocked::add);
        root.add("blocked_sources", blocked);
        JsonObject remembered = new JsonObject();
        decisions.forEach(remembered::addProperty);
        root.add("decisions", remembered);
        return root;
    }
}
