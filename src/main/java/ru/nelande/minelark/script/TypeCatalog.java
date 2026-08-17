package ru.nelande.minelark.script;

import java.util.Set;

/**
 * The valid names for the extensible, name-based block/item options, used by {@link StartupApi} for
 * fail-fast validation. Just strings, so the script layer stays free of Minecraft types: the adapter
 * builds a live catalog from {@code MinelarkTypes} (built-in defaults plus any addon-registered
 * types) and passes it in. Armor materials are also allowed as a bare {@code namespace:id} (they are
 * registry-backed), so this set holds only the aliases.
 */
public record TypeCatalog(
        Set<String> sounds,
        Set<String> toolTiers,
        Set<String> shapes,
        Set<String> armorMaterials
) {
    /** The built-in vanilla names, used when no live catalog is supplied (e.g. in unit tests). */
    public static final TypeCatalog VANILLA_DEFAULTS = new TypeCatalog(
            Set.of("stone", "wood", "gravel", "grass", "metal", "glass", "wool", "sand",
                    "snow", "ladder", "anvil", "slime", "honey", "bamboo", "nether"),
            Set.of("wood", "stone", "iron", "gold", "diamond", "netherite"),
            Set.of("slab", "stairs", "fence", "wall"),
            Set.of("leather", "chainmail", "iron", "gold", "diamond", "netherite", "turtle"));
}
