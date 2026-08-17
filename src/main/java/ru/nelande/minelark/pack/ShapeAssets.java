package ru.nelande.minelark.pack;

import java.util.Map;

/**
 * The resource/data-pack assets a block shape needs: its blockstate, block models, the block model
 * its item inherits, and any extra loot functions (e.g. a slab's double-drop). Deliberately free of
 * Minecraft types (pure JSON strings) so it can be unit-tested and registered from any mod. Built-in
 * shapes live in {@link ShapeAssetRegistry}; addon mods register their own via
 * {@code MinelarkTypes.shape(...)}.
 */
public interface ShapeAssets {
    /** The blockstate JSON for a block with this shape and the given id. */
    String blockstate(String id);

    /** The block models to emit, keyed by model file name (without the {@code .json}). */
    Map<String, String> blockModels(String id);

    /** The block-model name the block's item model should inherit (e.g. {@code <id>_inventory}). */
    String itemParentModel(String id);

    /** Extra loot-entry functions JSON (with a trailing comma), or {@code ""}. Used for slab doubling. */
    default String selfDropLootFunctions(String id) {
        return "";
    }
}
