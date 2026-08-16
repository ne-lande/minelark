package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

/**
 * The {@code log} namespace available to every script phase - Python-{@code logging}-style leveled
 * output, e.g. {@code log.info("hi")}. Messages are tagged with the current script's name and level.
 * (For quick output, the standard {@code print(...)} builtin also works.)
 */
public final class Log implements StarlarkValue {
    private final ScriptLog sink;
    private String source = "";

    public Log(ScriptLog sink) {
        this.sink = sink;
    }

    @StarlarkMethod(
            name = "debug",
            doc = "Logs a debug message to the game log / console.",
            parameters = @Param(name = "message", doc = "The value to log."))
    public void debug(Object message) {
        emit(ScriptLog.Level.DEBUG, message);
    }

    @StarlarkMethod(
            name = "info",
            doc = "Logs an informational message to the game log / console.",
            parameters = @Param(name = "message", doc = "The value to log."))
    public void info(Object message) {
        emit(ScriptLog.Level.INFO, message);
    }

    @StarlarkMethod(
            name = "warning",
            doc = "Logs a warning message to the game log / console.",
            parameters = @Param(name = "message", doc = "The value to log."))
    public void warning(Object message) {
        emit(ScriptLog.Level.WARNING, message);
    }

    @StarlarkMethod(
            name = "error",
            doc = "Logs an error message to the game log / console.",
            parameters = @Param(name = "message", doc = "The value to log."))
    public void error(Object message) {
        emit(ScriptLog.Level.ERROR, message);
    }

    private void emit(ScriptLog.Level level, Object message) {
        sink.log(level, "[" + source + "] " + Starlark.str(message));
    }

    /** Set by the engine before executing each script, so messages carry the right name. */
    void setSource(String source) {
        this.source = source;
    }

    String source() {
        return source;
    }
}
