package ru.nelande.minelark.script;

/**
 * Everything the client-phase scripts produced: the registered event callbacks (client lifecycle,
 * tick, tooltip, chat), the {@code debug} overlay lines and {@code hud} elements they set, the
 * the {@code net} channel handlers, and how many top-level scripts ran. Mirrors {@link ServerResult}.
 */
public record ClientResult(Events events, DebugApi debug, HudApi hud, ClientNetworkApi network, int scriptCount) {
}
