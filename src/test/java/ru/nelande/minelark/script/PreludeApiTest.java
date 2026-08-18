package ru.nelande.minelark.script;

import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1 tests for the prelude standard-library helpers. */
class PreludeApiTest {

    private final PreludeApi prelude = new PreludeApi();

    @Test
    void requirePassesWhenTruthyFailsOtherwise() throws EvalException {
        prelude.require(true, "ok");                 // does not throw
        prelude.require(StarlarkInt.of(1), "ok");    // truthy
        EvalException error = assertThrows(EvalException.class, () -> prelude.require(false, "nope"));
        assertTrue(error.getMessage().contains("nope"));
    }

    @Test
    void clampPreservesIntOrFloat() throws EvalException {
        assertEquals(StarlarkInt.of(5), prelude.clamp(StarlarkInt.of(9), StarlarkInt.of(0), StarlarkInt.of(5)));
        assertEquals(StarlarkInt.of(0), prelude.clamp(StarlarkInt.of(-3), StarlarkInt.of(0), StarlarkInt.of(5)));
        assertEquals(StarlarkFloat.of(0.5),
                prelude.clamp(StarlarkFloat.of(0.5), StarlarkInt.of(0), StarlarkInt.of(1)));
    }

    @Test
    void lerpBlends() throws EvalException {
        assertEquals(StarlarkFloat.of(5.0),
                prelude.lerp(StarlarkInt.of(0), StarlarkInt.of(10), StarlarkFloat.of(0.5)));
    }

    @Test
    void rgbBuildsHexAndRejectsOutOfRange() throws EvalException {
        assertEquals("#ff8000", prelude.rgb(StarlarkInt.of(255), StarlarkInt.of(128), StarlarkInt.of(0)));
        assertThrows(EvalException.class,
                () -> prelude.rgb(StarlarkInt.of(256), StarlarkInt.of(0), StarlarkInt.of(0)));
    }

    @Test
    void distIsEuclidean() throws EvalException {
        assertEquals(StarlarkFloat.of(5.0), prelude.dist(
                StarlarkInt.of(0), StarlarkInt.of(0), StarlarkInt.of(0),
                StarlarkInt.of(3), StarlarkInt.of(4), StarlarkInt.of(0)));
    }

    @Test
    void randIntStaysInRangeAndValidates() throws EvalException {
        assertEquals(StarlarkInt.of(5), prelude.randInt(StarlarkInt.of(5), StarlarkInt.of(5)));
        for (int i = 0; i < 100; i++) {
            int v = prelude.randInt(StarlarkInt.of(3), StarlarkInt.of(7)).toIntUnchecked();
            assertTrue(v >= 3 && v <= 7, "out of range: " + v);
        }
        assertThrows(EvalException.class, () -> prelude.randInt(StarlarkInt.of(9), StarlarkInt.of(1)));
    }

    @Test
    void choiceReturnsAMemberAndRejectsEmpty() throws EvalException {
        StarlarkList<StarlarkInt> options = StarlarkList.immutableCopyOf(
                List.of(StarlarkInt.of(10), StarlarkInt.of(20), StarlarkInt.of(30)));
        for (int i = 0; i < 50; i++) {
            assertTrue(options.contains(prelude.choice(options)), "not a member");
        }
        assertThrows(EvalException.class, () -> prelude.choice(StarlarkList.immutableCopyOf(List.of())));
    }

    @Test
    void jsonRoundTripsThroughAScript(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("j.star"), """
                data = from_json(to_json({"n": 3, "items": ["a", "b"]}))
                log.info("n=" + str(data["n"]) + " first=" + data["items"][0])
                """);
        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, log);
        assertTrue(log.anyMessageContains("n=3 first=a"), "got " + log.messages);
    }

    @Test
    void availableInEveryPhaseIncludingScripts(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("s.star"), """
                require(1 + 1 == 2, "math is broken")
                log.info("clamped=" + str(clamp(9, 0, 5)) + " color=" + rgb(255, 128, 0))
                """);

        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, log);

        assertTrue(log.anyMessageContains("clamped=5 color=#ff8000"), "got " + log.messages);
    }

    @Test
    void requireStopsAScriptFromTheTopLevel(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("s.star"), """
                require(False, "this pack needs something")
                log.info("should not reach here")
                """);

        TestLog log = new TestLog();
        StarlarkHost.runServer(dir, log);

        assertEquals(ScriptLog.Level.ERROR, log.levelOfMessageContaining("this pack needs something"));
        assertTrue(log.messages.stream().noneMatch(m -> m.contains("should not reach here")));
    }
}
