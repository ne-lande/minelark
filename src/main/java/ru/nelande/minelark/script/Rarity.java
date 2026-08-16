package ru.nelande.minelark.script;

/**
 * Loader-agnostic item rarity (controls the item name's colour). Mirrors vanilla's four tiers; the
 * Minecraft adapter maps these to {@code net.minecraft.util.Rarity}.
 */
public enum Rarity {
    COMMON, UNCOMMON, RARE, EPIC
}
