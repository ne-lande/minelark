package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1 tests for the live REPL engine behind the in-game console. */
class ConsoleSessionTest {

    private static ConsoleSession session() {
        // A minimal environment: just the standard builtins (no game namespaces needed here).
        return new ConsoleSession(Map.of());
    }

    @Test
    void printsTrailingExpressionValue() {
        assertEquals(List.of("3"), session().eval("1 + 2"));
        assertEquals(List.of("\"hi\""), session().eval("\"hi\""));
    }

    @Test
    void statementsProduceNoValueLine() {
        // A bare assignment is not an expression, so there is nothing to echo.
        assertEquals(List.of(), session().eval("x = 5"));
    }

    @Test
    void statePersistsAcrossCalls() {
        ConsoleSession s = session();
        assertEquals(List.of(), s.eval("count = 10"));
        assertEquals(List.of("11"), s.eval("count + 1"));

        // A function defined on one line is callable on the next.
        s.eval("def double(n): return n * 2");
        assertEquals(List.of("14"), s.eval("double(7)"));
    }

    @Test
    void globalsCanBeRebound() {
        ConsoleSession s = session();
        s.eval("x = 1");
        s.eval("x = 2");
        assertEquals(List.of("2"), s.eval("x"));
    }

    @Test
    void printOutputIsCaptured() {
        assertEquals(List.of("hello", "world"),
                session().eval("print(\"hello\")\nprint(\"world\")"));
    }

    @Test
    void printThenValueBothShow() {
        assertEquals(List.of("side effect", "42"),
                session().eval("print(\"side effect\")\n42"));
    }

    @Test
    void syntaxErrorIsReported() {
        List<String> out = session().eval("def (");
        assertTrue(out.stream().anyMatch(line -> line.startsWith("syntax error:")), "got " + out);
    }

    @Test
    void evalErrorIsReported() {
        List<String> out = session().eval("1 // 0");
        assertTrue(out.stream().anyMatch(line -> line.startsWith("error:")), "got " + out);
    }

    @Test
    void namespaceIsReachableFromTheConsole() {
        // The real console is built with game namespaces; prove one is callable here with a fake.
        Storage storage = new Storage(null);
        ConsoleSession s = new ConsoleSession(Map.of("storage", storage));
        assertEquals(List.of(), s.eval("storage.set(\"k\", 7)"));
        assertEquals(List.of("7"), s.eval("storage.get(\"k\")"));
    }
}
