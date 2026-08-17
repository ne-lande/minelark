package ru.nelande.minelark.script;

/**
 * The game-side hooks the server {@code net} namespace needs to send packets, implemented by the
 * server adapter. Kept as an interface so the {@code script} package stays free of Minecraft types
 * (mirrors {@link PlayerActions}). Payloads carry a channel name and a JSON string.
 */
public interface ServerNetwork {
    /** Sends {@code json} on {@code channel} to the player with this uuid (no-op if they are offline). */
    void sendToPlayer(String uuid, String channel, String json);

    /** Sends {@code json} on {@code channel} to every connected player. */
    void broadcast(String channel, String json);

    /** A sink that drops everything - for tests and phases with no live server. */
    ServerNetwork NOOP = new ServerNetwork() {
        @Override
        public void sendToPlayer(String uuid, String channel, String json) {
        }

        @Override
        public void broadcast(String channel, String json) {
        }
    };
}
