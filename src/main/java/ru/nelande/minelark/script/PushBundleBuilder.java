package ru.nelande.minelark.script;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans a server's {@code push/} folder and builds the {@link PushBundle} it will offer to clients.
 *
 * <p>The rules mirror {@link ScriptEngine}'s "run the top level, {@code load()} from subfolders":
 * <ul>
 *   <li>A <b>top-level</b> {@code .star} file is a pushable <b>entry point</b> only if its head carries
 *       the directive {@code # minelark: push} (optionally {@code # minelark: push capabilities=hud,net}).
 *       Unflagged top-level files are ignored, so a work-in-progress script is never exposed by accident.</li>
 *   <li>{@code .star} files in <b>subfolders</b> (e.g. {@code lib/helpers.star}) are always shipped as
 *       support files, so an entry's {@code load()} calls resolve on the client. They never run on their
 *       own (the engine only runs top-level files).</li>
 * </ul>
 *
 * <p>The requested capability set is the union of what the entry directives declare (an entry with no
 * explicit {@code capabilities=} contributes the secure {@link Capability#VISUAL_DEFAULTS}). Every file
 * is fingerprinted with SHA-256 for the manifest; a bundle exceeding the file-count or total-size cap
 * is refused (guards a malicious or runaway push). MC-agnostic and unit-testable.
 */
public final class PushBundleBuilder {
    /** Matches the opt-in directive at the head of a pushable script; group 1 is an optional cap list. */
    private static final Pattern DIRECTIVE = Pattern.compile(
            "^#\\s*minelark:\\s*push\\b(?:\\s+capabilities=([\\w,]+))?", Pattern.CASE_INSENSITIVE);

    /** How many head lines to scan for the directive (it must be near the top). */
    private static final int HEAD_LINES = 10;

    private final int maxFiles;
    private final long maxTotalBytes;

    /** A builder with the default caps: at most 64 files totalling at most 1 MB. */
    public PushBundleBuilder() {
        this(64, 1_000_000L);
    }

    public PushBundleBuilder(int maxFiles, long maxTotalBytes) {
        this.maxFiles = maxFiles;
        this.maxTotalBytes = maxTotalBytes;
    }

    /**
     * Builds the bundle for {@code pushDir}. Returns {@link PushBundle#EMPTY} if the folder is missing
     * or has no flagged entry points.
     *
     * @throws TooLarge if the flagged content exceeds the file-count or total-size cap
     */
    public PushBundle build(Path pushDir) {
        if (pushDir == null || !Files.isDirectory(pushDir)) {
            return PushBundle.EMPTY;
        }
        Path root = pushDir.toAbsolutePath().normalize();

        List<Path> starFiles;
        try (Stream<Path> walk = Files.walk(root)) {
            starFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".star"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan push folder " + root, e);
        }

        // First pass: find the flagged top-level entries and gather the requested capabilities.
        Set<Capability> requested = EnumSet.noneOf(Capability.class);
        Set<Path> entries = new java.util.HashSet<>();
        for (Path file : starFiles) {
            boolean topLevel = file.getParent().equals(root);
            if (!topLevel) {
                continue;
            }
            Optional<Set<Capability>> declared = parseDirective(read(file));
            if (declared.isPresent()) {
                entries.add(file);
                requested.addAll(declared.get());
            }
        }
        if (entries.isEmpty()) {
            return PushBundle.EMPTY;   // nothing opted in; offer nothing
        }

        // Second pass: collect the entries plus every subfolder support file, honouring the caps.
        Map<String, String> bodies = new LinkedHashMap<>();
        List<PushManifest.Entry> manifestEntries = new ArrayList<>();
        long total = 0;
        for (Path file : starFiles) {
            boolean topLevel = file.getParent().equals(root);
            if (topLevel && !entries.contains(file)) {
                continue;   // unflagged top-level file: not exposed
            }
            String name = root.relativize(file).toString().replace('\\', '/');
            String body = read(file);
            long size = body.getBytes(StandardCharsets.UTF_8).length;
            total += size;
            if (bodies.size() + 1 > maxFiles) {
                throw new TooLarge("push bundle has more than " + maxFiles + " files");
            }
            if (total > maxTotalBytes) {
                throw new TooLarge("push bundle exceeds " + maxTotalBytes + " bytes");
            }
            bodies.put(name, body);
            manifestEntries.add(new PushManifest.Entry(name, Sha256.hex(body), size));
        }
        return new PushBundle(PushManifest.of(manifestEntries, requested), bodies);
    }

    /**
     * Returns the capabilities a script's head declares if it carries the push directive, or empty if
     * it is not flagged. A flagged script with no explicit {@code capabilities=} yields the secure
     * visual defaults.
     */
    static Optional<Set<Capability>> parseDirective(String body) {
        String[] lines = body.split("\n", HEAD_LINES + 1);
        int limit = Math.min(lines.length, HEAD_LINES);
        for (int i = 0; i < limit; i++) {
            Matcher m = DIRECTIVE.matcher(lines[i].trim());
            if (m.find()) {
                String caps = m.group(1);
                if (caps == null || caps.isBlank()) {
                    return Optional.of(EnumSet.copyOf(Capability.VISUAL_DEFAULTS));
                }
                return Optional.of(Capability.parse(List.of(caps.split(","))));
            }
        }
        return Optional.empty();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read push script " + file, e);
        }
    }

    /** Thrown when a push folder's flagged content is too large to offer. */
    public static final class TooLarge extends RuntimeException {
        public TooLarge(String message) {
            super(message);
        }
    }
}
