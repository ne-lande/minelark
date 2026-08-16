package ru.nelande.minelark.script;

/**
 * A level-aware sink for messages produced while running scripts - both script output
 * ({@code print}, {@code console.*}) and Minelark's own diagnostics (syntax/eval errors).
 *
 * <p>MC-agnostic: the game adapter routes these to its logger; tests collect them.
 */
@FunctionalInterface
public interface ScriptLog {
    enum Level {
        DEBUG, INFO, WARNING, ERROR
    }

    void log(Level level, String message);

    default void debug(String message) {
        log(Level.DEBUG, message);
    }

    default void info(String message) {
        log(Level.INFO, message);
    }

    default void warning(String message) {
        log(Level.WARNING, message);
    }

    default void error(String message) {
        log(Level.ERROR, message);
    }
}
