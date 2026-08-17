package ru.nelande.minelark.api;

/**
 * Entrypoint for addon mods to register extra content types with Minelark. Implement this and declare
 * it under the {@code minelark:types} entrypoint in your {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": {
 *   "minelark:types": ["com.example.MyMinelarkTypes"]
 * }
 * }</pre>
 *
 * Minelark invokes every registered initializer during its own init, before startup scripts run, so
 * any names you {@link MinelarkTypes#sound register} are valid by the time a pack script uses them.
 */
@FunctionalInterface
public interface MinelarkTypesInitializer {
    /** Register your sound groups, tool tiers, armor materials, and shapes via {@link MinelarkTypes}. */
    void registerMinelarkTypes();
}
