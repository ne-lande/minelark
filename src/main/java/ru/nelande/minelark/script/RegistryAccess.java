package ru.nelande.minelark.script;

import java.util.List;

/**
 * Read-only access to the game registries, implemented by the game adapter. Kept as an interface so
 * the {@code script} package stays free of Minecraft types (mirrors {@link ClientAccess}). Backs the
 * {@code registry} namespace, letting packs check whether an item/block/etc. exists and list the ids
 * other mods registered - without any reflection.
 */
public interface RegistryAccess {
    /** The registries scripts can query. */
    enum Kind { ITEM, BLOCK, ENTITY_TYPE, FLUID }

    /** Whether the given (fully-qualified) id is present in the registry. */
    boolean has(Kind kind, String id);

    /** Every id in the registry, sorted. When {@code namespace} is non-empty, only that namespace. */
    List<String> ids(Kind kind, String namespace);

    /** A stand-in used when no game is available (e.g. in unit tests): every registry is empty. */
    RegistryAccess EMPTY = new RegistryAccess() {
        @Override public boolean has(Kind kind, String id) { return false; }
        @Override public List<String> ids(Kind kind, String namespace) { return List.of(); }
    };
}
