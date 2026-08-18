package ru.nelande.minelark.console;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ru.nelande.minelark.script.ConsoleSession;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A tiny web console: a loopback-only HTTP server (JDK built-in, no extra dependency) that serves a
 * browser code editor and evaluates snippets against a {@link ConsoleSession}. MC-agnostic - it takes
 * the session and an {@link Executor} that hops work onto the game thread, so both the HTTP handling
 * and the eval bridge are unit-testable.
 *
 * <p>Security posture: bound to {@code 127.0.0.1} only, and {@code /eval} requires a token (generated
 * per run, handed out in the URL the game logs). The mod also leaves it <b>off by default</b>. Even
 * so, remember the endpoint runs Starlark - which is sandboxed, so the blast radius is the console's
 * curated API, not arbitrary Java.
 */
public final class ConsoleServer {
    private static final Gson GSON = new Gson();
    private static final long EVAL_TIMEOUT_SECONDS = 15;

    private final HttpServer http;
    private final ConsoleSession session;
    private final Executor gameThread;
    private final String token;
    private final int port;
    private final String page;
    private final String apiJson;

    /**
     * Binds a console server to {@code 127.0.0.1:port}. Does not start it yet.
     *
     * @param session    the persistent REPL the console evaluates against
     * @param apiJson    the full self-describing API manifest, served (token-free) at {@code /api}
     * @param gameThread runs a task on the game thread (a {@code MinecraftServer} is such an executor)
     */
    public ConsoleServer(int port, String token, ConsoleSession session, String apiJson, Executor gameThread)
            throws IOException {
        this.session = session;
        this.apiJson = apiJson;
        this.gameThread = gameThread;
        this.token = token;
        this.page = loadPage();
        this.http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        // The bound port (if 0 was requested, the OS picked one - useful for tests).
        this.port = http.getAddress().getPort();
        http.createContext("/", this::handleRoot);
        http.createContext("/symbols", this::handleSymbols);
        http.createContext("/api", this::handleApi);
        http.createContext("/eval", this::handleEval);
        http.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "minelark-console");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public void start() {
        http.start();
    }

    public void stop() {
        http.stop(0);
    }

    /** The address to open, including the access token. */
    public String url() {
        return "http://127.0.0.1:" + port + "/?token=" + token;
    }

    public int port() {
        return port;
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "text/html; charset=utf-8", page);
    }

    /** The autocomplete manifest (public API names, so no token needed - the same info as the docs). */
    private void handleSymbols(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "application/json", session.symbolsJson());
    }

    /** The full self-describing API manifest for this build (public info, so no token needed). */
    private void handleApi(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "application/json", apiJson);
    }

    private void handleEval(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "text/plain", "POST only");
            return;
        }
        if (!tokenOk(exchange.getRequestHeaders().getFirst("X-Minelark-Token"))) {
            respond(exchange, 401, "text/plain", "invalid or missing token");
            return;
        }
        String code;
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            code = JsonParser.parseString(body).getAsJsonObject().get("code").getAsString();
        } catch (RuntimeException malformed) {
            respond(exchange, 400, "text/plain", "expected {\"code\": \"...\"}");
            return;
        }
        respond(exchange, 200, "application/json", GSON.toJson(evalResult(runOnGameThread(code))));
    }

    /** Evaluates on the game thread and waits for the result (so game state is touched safely). */
    private List<String> runOnGameThread(String code) {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        gameThread.execute(() -> {
            try {
                future.complete(session.eval(code));
            } catch (Throwable t) {
                future.complete(List.of("internal error: " + t));
            }
        });
        try {
            return future.get(EVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of("interrupted");
        } catch (Exception timedOut) {
            return List.of("timed out (is the game paused?)");
        }
    }

    private static JsonObject evalResult(List<String> output) {
        JsonArray lines = new JsonArray();
        output.forEach(lines::add);
        JsonObject result = new JsonObject();
        result.add("output", lines);
        return result;
    }

    private boolean tokenOk(String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String loadPage() throws IOException {
        try (var in = ConsoleServer.class.getResourceAsStream("/minelark/console.html")) {
            if (in == null) {
                return "<!doctype html><title>Minelark Console</title><p>console.html missing from the jar.";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
