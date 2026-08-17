package ru.nelande.minelark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minelark's small configuration, read from {@code <gamedir>/minelark/config.json} at startup. Free of
 * Minecraft types (takes a plain {@link Path}), so it is unit-testable. Missing files and missing
 * fields fall back to defaults, and a default file is written the first time so it is discoverable.
 *
 * <p>The web console is <b>off by default</b>: it evaluates code, so it is opt-in, bound to loopback,
 * and token-guarded (see {@code ConsoleServer}).
 */
public final class MinelarkConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean webConsoleEnabled = false;
    public int webConsolePort = 25599;

    /** Reads the config at {@code file}, writing a default file first if none exists. */
    public static MinelarkConfig load(Path file) {
        MinelarkConfig config = new MinelarkConfig();
        try {
            if (!Files.exists(file)) {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.writeString(file, GSON.toJson(config.toJson()));
                return config;
            }
            String text = Files.readString(file);
            if (text.isBlank()) {
                return config;
            }
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonObject console = root.has("web_console") ? root.getAsJsonObject("web_console") : new JsonObject();
            if (console.has("enabled")) {
                config.webConsoleEnabled = console.get("enabled").getAsBoolean();
            }
            if (console.has("port")) {
                config.webConsolePort = console.get("port").getAsInt();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Minelark config " + file, e);
        } catch (RuntimeException malformed) {
            // A corrupt config falls back to defaults rather than breaking startup.
            return new MinelarkConfig();
        }
        return config;
    }

    private JsonObject toJson() {
        JsonObject console = new JsonObject();
        console.addProperty("enabled", webConsoleEnabled);
        console.addProperty("port", webConsolePort);
        JsonObject root = new JsonObject();
        root.add("web_console", console);
        return root;
    }
}
