package ru.nelande.minelark.script;

import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * A namespace of events, e.g. {@code events.minelark}. Each event is exposed as a typed constant so
 * a typo fails fast ("no such field") rather than silently never firing. New core events are added
 * here as new {@code structField} accessors.
 */
public final class EventNamespace implements StarlarkValue {
    private final Events events;
    private final String namespace;

    EventNamespace(Events events, String namespace) {
        this.events = events;
        this.namespace = namespace;
    }

    @StarlarkMethod(
            name = "SERVER_STARTED",
            structField = true,
            doc = "Fires once when the server / world has finished loading.")
    public Event serverStarted() {
        return event("server_started");
    }

    @StarlarkMethod(
            name = "SERVER_TICK",
            structField = true,
            doc = "Fires at the end of every server tick (20 times a second). `ctx` carries no data.")
    public Event serverTick() {
        return event("server_tick");
    }

    @StarlarkMethod(
            name = "PLAYER_JOINED",
            structField = true,
            doc = "Fires when a player finishes joining. `ctx.player` is the player.")
    public Event playerJoined() {
        return event("player_joined");
    }

    @StarlarkMethod(
            name = "PLAYER_LEFT",
            structField = true,
            doc = "Fires when a player disconnects. `ctx.player` is the player.")
    public Event playerLeft() {
        return event("player_left");
    }

    @StarlarkMethod(
            name = "PLAYER_DEATH",
            structField = true,
            doc = "Fires when a player is about to die. Cancellable (keeps them alive). `ctx.player`, "
                    + "`ctx.source` (the damage type name), and `ctx.amount` (the final blow).")
    public Event playerDeath() {
        return event("player_death");
    }

    @StarlarkMethod(
            name = "PLAYER_CHAT",
            structField = true,
            doc = "Fires when a player sends a chat message. Cancellable. `ctx.player` and "
                    + "`ctx.message` (editable: reassign `ctx.message` to rewrite the line).")
    public Event playerChat() {
        return event("player_chat");
    }

    @StarlarkMethod(
            name = "BLOCK_BROKEN",
            structField = true,
            doc = "Fires just before a player breaks a block. Cancellable. `ctx.player`, `ctx.block` "
                    + "(the block id), and `ctx.x` / `ctx.y` / `ctx.z`.")
    public Event blockBroken() {
        return event("block_broken");
    }

    @StarlarkMethod(
            name = "BLOCK_PLACED",
            structField = true,
            doc = "Fires just before a player places a block. Cancellable. `ctx.player`, `ctx.block` "
                    + "(the block id), and `ctx.x` / `ctx.y` / `ctx.z`.")
    public Event blockPlaced() {
        return event("block_placed");
    }

    @StarlarkMethod(
            name = "COMMAND",
            structField = true,
            doc = "Fires before a command runs. Cancellable. `ctx.player` (may be absent for the "
                    + "console), `ctx.command` (editable: reassign to rewrite the command).")
    public Event command() {
        return event("command");
    }

    @StarlarkMethod(
            name = "EXPLOSION",
            structField = true,
            doc = "Fires when an explosion goes off (notification). `ctx.x` / `ctx.y` / `ctx.z` and "
                    + "`ctx.power`.")
    public Event explosion() {
        return event("explosion");
    }

    private Event event(String path) {
        return new Event(events, namespace + ":" + path);
    }
}
