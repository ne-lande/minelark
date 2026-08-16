package ru.nelande.minelark.script;

/**
 * One argument of a script-registered command: its name (the key it appears under in
 * {@code ctx.args}) and its type. Types are curated - the adapter maps each to a Brigadier argument.
 */
public record ArgSpec(String name, String type) {

    /** The argument types a script may ask for. */
    public static final java.util.Set<String> TYPES =
            java.util.Set.of("word", "string", "int", "float", "bool", "player");
}
