package ru.nelande.minelark.script;

import java.util.List;

/**
 * Read-only access to the mod loader, implemented by the game adapter. Kept as an interface so the
 * {@code script} package stays free of Minecraft / Fabric types (mirrors {@link ClientAccess} and
 * {@link PlayerActions}). Backs the {@code mods} namespace, which lets packs branch on what else is
 * installed - the sandbox-preserving half of the "curated Java bridge": scripts can discover other
 * mods, but never reach into them via reflection.
 */
public interface PlatformInfo {
    /** Whether a mod with the given id is loaded. */
    boolean isLoaded(String modId);

    /** The mod's version string, or {@code null} if it is not loaded. */
    String version(String modId);

    /** The mod's human-readable name, or {@code null} if it is not loaded. */
    String name(String modId);

    /** Every loaded mod id, sorted. */
    List<String> ids();

    /** A stand-in used when no loader is available (e.g. in unit tests): nothing is loaded. */
    PlatformInfo EMPTY = new PlatformInfo() {
        @Override public boolean isLoaded(String modId) { return false; }
        @Override public String version(String modId) { return null; }
        @Override public String name(String modId) { return null; }
        @Override public List<String> ids() { return List.of(); }
    };
}
