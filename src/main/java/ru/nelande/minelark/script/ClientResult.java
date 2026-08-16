package ru.nelande.minelark.script;

/**
 * Everything the client-phase scripts produced: the registered event callbacks (client lifecycle,
 * tick, tooltip, chat), the {@code debug} overlay lines they set, and how many top-level scripts ran.
 * Mirrors {@link ServerResult}.
 */
public record ClientResult(Events events, DebugApi debug, int scriptCount) {
}
