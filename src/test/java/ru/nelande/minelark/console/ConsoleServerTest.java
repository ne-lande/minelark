package ru.nelande.minelark.console;

import org.junit.jupiter.api.Test;
import ru.nelande.minelark.script.ConsoleSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for the web console's HTTP layer. The server is MC-agnostic, so we run it on an
 * ephemeral port with a synchronous "game thread" ({@code Runnable::run}) and talk to it over real
 * HTTP - no Minecraft required.
 */
class ConsoleServerTest {

    private static ConsoleServer server(String token) throws Exception {
        return new ConsoleServer(0, token, new ConsoleSession(Map.of()), Runnable::run);
    }

    @Test
    void evalReturnsOutputWithValidToken() throws Exception {
        ConsoleServer server = server("secret");
        server.start();
        try {
            HttpResponse<String> response = post(server, "secret", "{\"code\": \"1 + 2\"}");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"3\""), response.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void statePersistsAcrossRequests() throws Exception {
        ConsoleServer server = server("t");
        server.start();
        try {
            post(server, "t", "{\"code\": \"n = 5\"}");
            HttpResponse<String> response = post(server, "t", "{\"code\": \"n + 1\"}");
            assertTrue(response.body().contains("\"6\""), response.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectsMissingOrWrongToken() throws Exception {
        ConsoleServer server = server("right");
        server.start();
        try {
            assertEquals(401, post(server, "wrong", "{\"code\": \"1\"}").statusCode());
            assertEquals(401, post(server, null, "{\"code\": \"1\"}").statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void servesTheEditorPage() throws Exception {
        ConsoleServer server = server("t");
        server.start();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Minelark Console"), "expected the console page");
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> post(ConsoleServer server, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + server.port() + "/eval"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("X-Minelark-Token", token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
