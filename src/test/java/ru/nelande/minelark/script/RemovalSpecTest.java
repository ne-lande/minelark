package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-2 tests for the recipe-removal filter matching (no Minecraft needed). */
class RemovalSpecTest {

    private static final String ID = "minecraft:oak_planks";
    private static final String TYPE = "minecraft:crafting_shapeless";
    private static final List<String> INPUTS = List.of("minecraft:oak_log");
    private static final String OUTPUT = "minecraft:oak_planks";

    private static boolean match(RemovalSpec spec) {
        return spec.matches(ID, TYPE, INPUTS, OUTPUT);
    }

    @Test
    void exactIdMatches() {
        assertTrue(match(new RemovalSpec("minecraft:oak_planks", null, null, null, null)));
        assertFalse(match(new RemovalSpec("minecraft:birch_planks", null, null, null, null)));
    }

    @Test
    void modNamespaceMatches() {
        assertTrue(match(new RemovalSpec(null, "minecraft", null, null, null)));
        assertFalse(match(new RemovalSpec(null, "create", null, null, null)));
    }

    @Test
    void typeInputOutputMatch() {
        assertTrue(match(new RemovalSpec(null, null, "minecraft:crafting_shapeless", null, null)));
        assertTrue(match(new RemovalSpec(null, null, null, "minecraft:oak_log", null)));
        assertTrue(match(new RemovalSpec(null, null, null, null, "minecraft:oak_planks")));
        assertFalse(match(new RemovalSpec(null, null, "minecraft:smelting", null, null)));
        assertFalse(match(new RemovalSpec(null, null, null, "minecraft:stone", null)));
    }

    @Test
    void fieldsAreAnded() {
        // both match -> removed
        assertTrue(match(new RemovalSpec(null, "minecraft", "minecraft:crafting_shapeless", null, null)));
        // one mismatches -> kept
        assertFalse(match(new RemovalSpec(null, "minecraft", "minecraft:smelting", null, null)));
    }

    @Test
    void nullInputsAreSafe() {
        assertFalse(new RemovalSpec(null, null, null, "minecraft:anything", null)
                .matches(ID, TYPE, null, OUTPUT));
    }
}
