package ru.nelande.minelark.script;

import com.google.common.collect.ImmutableMap;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.SyntaxError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A live Starlark REPL: evaluates snippets one at a time against a <b>persistent</b> module, so a
 * variable or function defined on one line is available on the next. Backs the in-game console
 * ({@code /minelark eval}). MC-agnostic, so it is unit-testable; the game only supplies the
 * predeclared globals and shuttles the input/output.
 *
 * <p>Unlike loading a script file (which freezes its module afterward), the session keeps its module
 * open across calls and allows re-binding a global, the way a REPL should. Each call still runs on a
 * fresh {@link StarlarkThread}, so values created by an earlier line are frozen when read by a later
 * one - fine for a console (you can still rebind the name).
 */
public final class ConsoleSession {
    private static final StarlarkSemantics SEMANTICS = StarlarkSemantics.DEFAULT;

    /** REPL options: let a later line re-bind a top-level name (e.g. redefine {@code x}). */
    private static final FileOptions OPTIONS = FileOptions.builder().allowToplevelRebinding(true).build();

    private final Module module;
    private final String symbolsJson;

    /** Creates a session whose scripts see {@code predeclared} (namespaces + builtins) as globals. */
    public ConsoleSession(Map<String, Object> predeclared) {
        // The standard Starlark builtins (print, len, range, ...) live in the universe, which is not
        // implicit for a module - merge it in so the console always has them (caller's map wins).
        Map<String, Object> env = new LinkedHashMap<>(Starlark.UNIVERSE);
        env.putAll(predeclared);
        this.module = Module.withPredeclared(SEMANTICS, ImmutableMap.copyOf(env));
        this.symbolsJson = ConsoleSymbols.toJson(env);
    }

    /** The autocomplete manifest (globals + namespace members) for this session's environment. */
    public String symbolsJson() {
        return symbolsJson;
    }

    /**
     * Evaluates one snippet and returns the lines to show: whatever it {@code print()}ed, then the
     * value of a trailing expression (if any), or a syntax/eval error. State persists into the next
     * call.
     */
    public List<String> eval(String source) {
        List<String> output = new ArrayList<>();
        try (Mutability mu = Mutability.create("console")) {
            StarlarkThread thread = new StarlarkThread(mu, SEMANTICS);
            thread.setPrintHandler((t, message) -> output.add(message));
            ParserInput input = ParserInput.fromString(source, "<console>");
            Object result = Starlark.execFile(input, OPTIONS, module, thread);
            if (result != Starlark.NONE && result != null) {
                output.add(Starlark.repr(result));
            }
        } catch (SyntaxError.Exception e) {
            for (SyntaxError error : e.errors()) {
                output.add("syntax error: " + error);
            }
        } catch (EvalException e) {
            output.add("error: " + e.getMessageWithStack());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.add("interrupted");
        }
        return output;
    }
}
