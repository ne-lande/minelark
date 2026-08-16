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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Runs the {@code .star} scripts in a phase folder against a fixed set of predeclared globals.
 *
 * <p>Top-level scripts are the {@code .star} files directly inside the root (non-recursive), run in
 * alphabetical order. Scripts may pull in shared helpers with Starlark's {@code load()} - labels are
 * resolved relative to the root (e.g. {@code load("lib/helpers.star", "foo")}); loaded modules are
 * cached (executed once) and import cycles are detected. A failure in one script is logged and the
 * others still run.
 *
 * <p>MC-agnostic: the predeclared globals decide what scripts can do.
 */
final class ScriptEngine {
    private static final StarlarkSemantics SEMANTICS = StarlarkSemantics.DEFAULT;

    private final Path root;
    private final ImmutableMap<String, Object> predeclared;
    private final Log console; // nullable
    private final ScriptLog log;

    private final Map<Path, Module> cache = new HashMap<>();
    private final Set<Path> loading = new LinkedHashSet<>();

    ScriptEngine(Path root, ImmutableMap<String, Object> predeclared, Log console, ScriptLog log) {
        this.root = root.toAbsolutePath().normalize();
        this.predeclared = predeclared;
        this.console = console;
        this.log = log;
    }

    /** Runs every top-level script; returns how many were attempted. */
    int runAll() {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        List<Path> scripts = listTopLevel();
        for (Path file : scripts) {
            String name = rel(file);
            try {
                exec(file);
            } catch (SyntaxError.Exception e) {
                for (SyntaxError error : e.errors()) {
                    log.error("[" + name + "] syntax error: " + error);
                }
            } catch (EvalException e) {
                log.error("[" + name + "] error: " + e.getMessageWithStack());
            } catch (LoadFailure e) {
                log.error("[" + name + "] " + e.getMessage());
            } catch (Starlark.UncheckedEvalException e) {
                // Starlark wraps unchecked exceptions thrown by our loader (e.g. LoadFailure);
                // surface the underlying message.
                log.error("[" + name + "] " + rootMessage(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[" + name + "] interrupted");
                break;
            }
        }
        return scripts.size();
    }

    private Module exec(Path file) throws SyntaxError.Exception, EvalException, InterruptedException {
        Path key = file.toAbsolutePath().normalize();
        Module cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (!loading.add(key)) {
            throw new LoadFailure("import cycle involving " + rel(file));
        }

        String name = rel(file);
        String previousSource = console != null ? console.source() : null;
        Module module = Module.withPredeclared(SEMANTICS, predeclared);
        try (Mutability mu = Mutability.create(name)) {
            StarlarkThread thread = new StarlarkThread(mu, SEMANTICS);
            thread.setPrintHandler((t, msg) -> log.info("[" + name + "] " + msg));
            thread.setLoader(label -> loadModule(label));
            if (console != null) {
                console.setSource(name);
            }
            String source = Files.readString(file);
            ParserInput input = ParserInput.fromString(source, name);
            Starlark.execFile(input, FileOptions.DEFAULT, module, thread);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read script " + file, e);
        } finally {
            if (console != null && previousSource != null) {
                console.setSource(previousSource);
            }
            loading.remove(key);
        }

        cache.put(key, module);
        return module;
    }

    /** Resolves and executes a {@code load()} target, surfacing failures as {@link LoadFailure}. */
    private Module loadModule(String label) {
        Path target = resolveLabel(label);
        try {
            return exec(target);
        } catch (SyntaxError.Exception e) {
            throw new LoadFailure("failed to load '" + label + "': syntax error");
        } catch (EvalException e) {
            throw new LoadFailure("failed to load '" + label + "': " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LoadFailure("interrupted while loading '" + label + "'");
        }
    }

    private Path resolveLabel(String label) {
        // Accept Bazel-ish labels too: strip a leading "//" and treat ':' as a path separator.
        String clean = label.startsWith("//") ? label.substring(2) : label;
        clean = clean.replace(':', '/');
        Path target = root.resolve(clean).normalize();
        if (!target.startsWith(root)) {
            throw new LoadFailure("load('" + label + "') escapes the scripts folder");
        }
        if (!Files.isRegularFile(target)) {
            throw new LoadFailure("load('" + label + "') not found");
        }
        return target;
    }

    private String rel(Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString();
    }

    private List<Path> listTopLevel() {
        try (Stream<Path> files = Files.list(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".star"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list scripts in " + root, e);
        }
    }

    /** Finds the most meaningful message in a (possibly nested) exception chain. */
    private static String rootMessage(Throwable t) {
        for (Throwable cur = t; cur != null && cur.getCause() != cur; cur = cur.getCause()) {
            if (cur instanceof LoadFailure) {
                return cur.getMessage();
            }
        }
        return t.getMessage();
    }

    /** Unchecked so load failures can propagate through {@code StarlarkThread.Loader.load()}. */
    static final class LoadFailure extends RuntimeException {
        LoadFailure(String message) {
            super(message);
        }
    }
}
