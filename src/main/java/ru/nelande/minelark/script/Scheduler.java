package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The {@code timers} namespace for <b>server</b> scripts: run a function later, or on a repeating
 * interval, measured in game ticks (20 ticks = 1 second). Handy for cooldowns, delayed effects, and
 * anything that should happen "in a bit" without hand-counting ticks in {@code SERVER_TICK}.
 *
 * <p>MC-agnostic and unit-testable: it just holds the queued tasks; the game adapter drives it by
 * calling {@link #tick} once per server tick. Callbacks take no arguments and run on a fresh
 * {@link StarlarkThread}, with errors routed to the script log (never escaping into the game). A
 * {@code /minelark reload} builds a fresh scheduler, so pending timers do not survive a reload.
 */
public final class Scheduler implements StarlarkValue {

    private static final class Task {
        final int id;
        final StarlarkCallable callback;
        int remaining;
        final int interval;   // 0 = one-shot; > 0 = reschedule this many ticks after firing

        Task(int id, StarlarkCallable callback, int remaining, int interval) {
            this.id = id;
            this.callback = callback;
            this.remaining = remaining;
            this.interval = interval;
        }
    }

    private final List<Task> tasks = new ArrayList<>();
    private int nextId = 1;

    @StarlarkMethod(
            name = "after",
            doc = "Runs `callback` once, `ticks` game ticks from now (20 ticks = 1 second). Returns a "
                    + "handle you can pass to `cancel`.",
            parameters = {
                    @Param(name = "ticks", doc = "How many ticks to wait (at least 1)."),
                    @Param(name = "callback", doc = "A function taking no arguments."),
            })
    public synchronized StarlarkInt after(StarlarkInt ticks, StarlarkCallable callback) throws EvalException {
        return schedule(ticks, callback, false);
    }

    @StarlarkMethod(
            name = "every",
            doc = "Runs `callback` every `ticks` game ticks (20 ticks = 1 second), starting `ticks` from "
                    + "now. Returns a handle you can pass to `cancel`.",
            parameters = {
                    @Param(name = "ticks", doc = "The interval in ticks (at least 1)."),
                    @Param(name = "callback", doc = "A function taking no arguments."),
            })
    public synchronized StarlarkInt every(StarlarkInt ticks, StarlarkCallable callback) throws EvalException {
        return schedule(ticks, callback, true);
    }

    @StarlarkMethod(
            name = "cancel",
            doc = "Cancels a timer by the handle `after`/`every` returned. Returns whether one was removed.",
            parameters = {@Param(name = "handle", doc = "The handle returned by `after` or `every`.")})
    public synchronized boolean cancel(StarlarkInt handle) {
        int id = handle.toIntUnchecked();
        return tasks.removeIf(task -> task.id == id);
    }

    private StarlarkInt schedule(StarlarkInt ticksInt, StarlarkCallable callback, boolean repeating)
            throws EvalException {
        int ticks = ticksInt.toIntUnchecked();
        if (ticks < 1) {
            throw Starlark.errorf("ticks must be at least 1, got %d", ticks);
        }
        int id = nextId++;
        tasks.add(new Task(id, callback, ticks, repeating ? ticks : 0));
        return StarlarkInt.of(id);
    }

    /** Whether any timers are pending, so the adapter can skip a tick cheaply. */
    public synchronized boolean isEmpty() {
        return tasks.isEmpty();
    }

    /**
     * Advances every timer by one tick and runs the ones that came due. Callbacks run outside the
     * lock, so a callback may itself schedule or cancel timers. Called once per server tick.
     */
    public void tick(ScriptLog sink) {
        List<StarlarkCallable> due = new ArrayList<>();
        synchronized (this) {
            if (tasks.isEmpty()) {
                return;
            }
            Iterator<Task> it = tasks.iterator();
            while (it.hasNext()) {
                Task task = it.next();
                if (--task.remaining <= 0) {
                    due.add(task.callback);
                    if (task.interval > 0) {
                        task.remaining = task.interval;
                    } else {
                        it.remove();
                    }
                }
            }
        }
        for (StarlarkCallable callback : due) {
            run(callback, sink);
        }
    }

    private static void run(StarlarkCallable callback, ScriptLog sink) {
        try (Mutability mu = Mutability.create("timer")) {
            StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
            thread.setPrintHandler((t, message) -> sink.info("[timer] " + message));
            Starlark.call(thread, callback, List.of(), Map.of());
        } catch (EvalException e) {
            sink.error("[timer] " + e.getMessageWithStack());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
