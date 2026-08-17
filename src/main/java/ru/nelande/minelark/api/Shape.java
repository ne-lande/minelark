package ru.nelande.minelark.api;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;

/**
 * Builds the {@link Block} for a scripted block of a given shape, from the settings Minelark has
 * already applied (strength, sound, light, etc.). Registered - together with its resource assets -
 * through {@link MinelarkTypes#shape}. Addon mods implement this to add shapes beyond the built-in
 * slab/stairs/fence/wall.
 */
@FunctionalInterface
public interface Shape {
    Block create(AbstractBlock.Settings settings);
}
