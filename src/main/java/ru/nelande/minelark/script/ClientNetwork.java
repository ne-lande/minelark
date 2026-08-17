package ru.nelande.minelark.script;

/**
 * The game-side hook the client {@code net} namespace needs to send packets to the server, implemented
 * by the client adapter. Kept as an interface so the {@code script} package stays free of Minecraft
 * types (mirrors {@link ClientAccess}). Payloads carry a channel name and a JSON string.
 */
public interface ClientNetwork {
    /** Sends {@code json} on {@code channel} to the server (no-op if not connected). */
    void sendToServer(String channel, String json);

    /** A sink that drops everything - for tests and phases with no live connection. */
    ClientNetwork NOOP = (channel, json) -> {
    };
}
