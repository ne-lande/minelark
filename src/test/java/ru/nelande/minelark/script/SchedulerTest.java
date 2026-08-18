package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the {@code timers} scheduler: {@code after} fires once at its delay, {@code every}
 * repeats, {@code cancel} stops a timer, callbacks may schedule more, and a bad delay is reported -
 * all by advancing the scheduler by hand, no game needed.
 */
class SchedulerTest {

    private static Scheduler load(Path dir, TestLog log, String script) throws IOException {
        Files.writeString(dir.resolve("t.star"), script);
        return StarlarkHost.runServer(dir, log).scheduler();
    }

    private static int count(TestLog log, String needle) {
        return (int) log.messages.stream().filter(m -> m.contains(needle)).count();
    }

    @Test
    void afterFiresOnceAtItsDelay(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Scheduler timers = load(dir, log, """
                def cb():
                    log.info("fired")
                timers.after(3, cb)
                """);

        timers.tick(log);
        timers.tick(log);
        assertEquals(0, count(log, "fired"), "not due yet");
        timers.tick(log);
        assertEquals(1, count(log, "fired"), "due on the third tick");
        timers.tick(log);
        timers.tick(log);
        assertEquals(1, count(log, "fired"), "one-shot does not repeat");
        assertTrue(timers.isEmpty());
    }

    @Test
    void everyRepeatsAtItsInterval(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Scheduler timers = load(dir, log, """
                def cb():
                    log.info("tick")
                timers.every(2, cb)
                """);

        for (int i = 0; i < 6; i++) {
            timers.tick(log);
        }
        assertEquals(3, count(log, "tick"), "fires every 2 ticks over 6 ticks");
        assertFalse(timers.isEmpty());
    }

    @Test
    void cancelStopsATimer(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Scheduler timers = load(dir, log, """
                def cb():
                    log.info("nope")
                h = timers.every(2, cb)
                timers.cancel(h)
                """);

        for (int i = 0; i < 6; i++) {
            timers.tick(log);
        }
        assertEquals(0, count(log, "nope"));
        assertTrue(timers.isEmpty());
    }

    @Test
    void aCallbackCanScheduleAnother(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Scheduler timers = load(dir, log, """
                def second():
                    log.info("second")
                def first():
                    log.info("first")
                    timers.after(1, second)
                timers.after(1, first)
                """);

        timers.tick(log);   // first
        assertEquals(1, count(log, "first"));
        assertEquals(0, count(log, "second"));
        timers.tick(log);   // second (scheduled by first)
        assertEquals(1, count(log, "second"));
    }

    @Test
    void aBadDelayIsReported(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        load(dir, log, """
                def cb():
                    log.info("x")
                timers.after(0, cb)
                """);
        assertTrue(log.anyMessageContains("at least 1"), "got " + log.messages);
    }

    @Test
    void aFailingCallbackIsCaughtNotThrown(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        Scheduler timers = load(dir, log, """
                def boom():
                    fail("kaboom")
                timers.after(1, boom)
                """);
        timers.tick(log);   // must not throw
        assertTrue(log.anyMessageContains("kaboom"), "got " + log.messages);
    }
}
