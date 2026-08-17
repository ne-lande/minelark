package ru.nelande.minelark.script;

import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;

import java.util.List;
import java.util.Map;

/**
 * Shared machinery for invoking script callbacks (event handlers, {@code net} channel handlers): runs
 * each one on a fresh {@link StarlarkThread}, tags {@code log.*} with the source, and routes errors to
 * the script log instead of letting them escape into the game. MC-agnostic.
 */
final class ScriptCallbacks {
    private ScriptCallbacks() {
    }

    /**
     * Invokes each callback with {@code ctx}. The same {@code ctx} is shared across callbacks so
     * mutations accumulate and the caller can read them back. {@code source} tags the log (e.g.
     * {@code "event:minelark:server_started"} or {@code "net:my_channel"}).
     */
    static void fire(String source, List<StarlarkCallable> callbacks, EventContext ctx, Log log, ScriptLog sink) {
        for (StarlarkCallable callback : List.copyOf(callbacks)) {
            log.setSource(source);
            try (Mutability mu = Mutability.create(source)) {
                StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
                thread.setPrintHandler((t, message) -> sink.info("[" + source + "] " + message));
                Starlark.call(thread, callback, List.of(ctx), Map.of());
            } catch (EvalException e) {
                sink.error("[" + source + "] " + e.getMessageWithStack());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sink.error("[" + source + "] interrupted");
                return;
            } finally {
                log.setSource("");
            }
        }
    }
}
