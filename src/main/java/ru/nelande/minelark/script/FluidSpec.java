package ru.nelande.minelark.script;

/**
 * A loader-agnostic description of a custom fluid declared by a startup script. The Minecraft adapter
 * turns each spec into a still + flowing fluid, a fluid block, and a bucket item.
 *
 * @param id          the fluid path, registered as {@code minelark:<id>} (flowing as {@code minelark:flowing_<id>})
 * @param displayName the bucket's shown name; empty means the default translation
 * @param luminance   light the fluid emits, 0..15
 * @param tint        an RGB colour applied to the fluid's textures (e.g. {@code 0x3F76E4} for water-blue)
 */
public record FluidSpec(
        String id,
        String displayName,
        int luminance,
        int tint
) {
    /** A plain, uncoloured fluid with no light. */
    public static FluidSpec basic(String id) {
        return new FluidSpec(id, "", 0, 0xFFFFFF);
    }
}
