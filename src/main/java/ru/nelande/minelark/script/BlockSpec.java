package ru.nelande.minelark.script;

import java.util.List;

/**
 * A loader-agnostic description of a block declared by a startup script. The Minecraft adapter turns
 * each spec into a block plus a matching block item.
 *
 * @param id           the block path, registered as {@code minelark:<id>}
 * @param hardness     mining time factor (vanilla stone is 1.5)
 * @param resistance   blast resistance (vanilla stone is 6.0)
 * @param luminance    emitted light level, 0..15
 * @param requiresTool whether the correct tool is required to drop anything
 * @param displayName  the shown name; empty means use the default {@code block.minelark.<id>} translation
 * @param tags         resolved block-tag ids ({@code namespace:path}) this block should belong to
 * @param drops        what the block drops when broken: {@code ""} = itself, {@code "none"} = nothing,
 *                     otherwise a resolved item id ({@code namespace:path})
 */
public record BlockSpec(
        String id,
        double hardness,
        double resistance,
        int luminance,
        boolean requiresTool,
        String displayName,
        List<String> tags,
        String drops
) {
    /** A plain block that drops itself, with modest strength and no other options. */
    public static BlockSpec basic(String id) {
        return new BlockSpec(id, 1.0, 1.0, 0, false, "", List.of(), "");
    }
}
