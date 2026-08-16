package ru.nelande.minelark.script;

import net.starlark.java.eval.Dict;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.StarlarkInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1 tests for the {@code commands} namespace: registration, arg specs, and handler dispatch. */
class CommandsApiTest {

    private static CommandsApi run(Path dir, TestLog log, String script) throws IOException {
        Files.writeString(dir.resolve("c.star"), script);
        return StarlarkHost.runServer(dir, log).commands();
    }

    private static Dict<String, Object> args(Object... pairs) {
        Dict.Builder<String, Object> b = Dict.builder();
        for (int i = 0; i < pairs.length; i += 2) {
            b.put((String) pairs[i], pairs[i + 1]);
        }
        return b.build(Mutability.IMMUTABLE);
    }

    private static CommandSourceView source(AtomicReference<MineText> feedback) {
        return new CommandSourceView("Steve", new PlayerView("Steve", "u", m -> {}),
                new LevelView("minecraft:overworld", 0, true, false), feedback::set);
    }

    @Test
    void registersWithArgsAndPermission(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        CommandsApi commands = run(dir, log, """
                def h(ctx):
                    pass
                commands.register("warp home", h, permission = 2,
                                  args = [{"name": "who", "type": "player"}])
                """);

        List<CommandSpec> specs = commands.commands();
        assertEquals(1, specs.size(), "got " + log.messages);
        CommandSpec spec = specs.get(0);
        assertEquals("warp home", spec.name());
        assertEquals(List.of("warp", "home"), spec.literals());
        assertEquals(2, spec.permission());
        assertEquals(1, spec.args().size());
        assertEquals("who", spec.args().get(0).name());
        assertEquals("player", spec.args().get(0).type());
    }

    @Test
    void handlerReadsArgsAndTellsSource(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        CommandsApi commands = run(dir, log, """
                def give_gold(ctx):
                    n = ctx.args["amount"]
                    ctx.source.tell(text("giving " + str(n) + " to " + ctx.source.name).color("gold"))

                commands.register("gold", give_gold, args = [{"name": "amount", "type": "int"}])
                """);

        AtomicReference<MineText> told = new AtomicReference<>();
        CommandContext ctx = new CommandContext(source(told), args("amount", StarlarkInt.of(7)));
        boolean ok = commands.invoke(commands.commands().get(0), ctx, log);

        assertTrue(ok, "got " + log.messages);
        assertEquals("giving 7 to Steve", told.get().literal());
        assertEquals("gold", told.get().colorValue());
    }

    @Test
    void handlerErrorIsReportedAndReturnsFalse(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        CommandsApi commands = run(dir, log, """
                def boom(ctx):
                    fail("nope")
                commands.register("boom", boom)
                """);

        boolean ok = commands.invoke(commands.commands().get(0),
                new CommandContext(source(new AtomicReference<>()), args()), log);

        assertTrue(!ok, "handler that fails should return false");
        assertTrue(log.anyMessageContains("nope"), "got " + log.messages);
    }

    @Test
    void tupleArgSpecAlsoWorks(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        CommandsApi commands = run(dir, log, """
                def h(ctx):
                    pass
                commands.register("say", h, args = [["text", "string"]])
                """);

        ArgSpec arg = commands.commands().get(0).args().get(0);
        assertEquals("text", arg.name());
        assertEquals("string", arg.type());
    }

    @Test
    void unknownArgTypeIsReportedNotThrown(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        CommandsApi commands = run(dir, log, """
                def h(ctx):
                    pass
                commands.register("x", h, args = [{"name": "a", "type": "banana"}])
                """);

        assertTrue(commands.commands().isEmpty(), "a bad spec should not register");
        assertTrue(log.anyMessageContains("unknown arg type 'banana'"), "got " + log.messages);
    }

    @Test
    void badPermissionIsReportedNotThrown(@TempDir Path dir) throws IOException {
        TestLog log = new TestLog();
        CommandsApi commands = run(dir, log, """
                def h(ctx):
                    pass
                commands.register("x", h, permission = 9)
                """);

        assertTrue(commands.commands().isEmpty());
        assertTrue(log.anyMessageContains("permission must be 0-4"), "got " + log.messages);
    }
}
