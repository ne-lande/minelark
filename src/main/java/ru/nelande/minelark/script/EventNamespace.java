package ru.nelande.minelark.script;

import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkValue;

/**
 * A namespace of events, e.g. {@code events.minelark}. Each event is exposed as a typed constant so
 * a typo fails fast ("no such field") rather than silently never firing. New core events are added
 * here as new {@code structField} accessors.
 *
 * <p>Events belong to a lifecycle phase (server or client). A namespace is scoped to the phase whose
 * script produced it, and referencing an event from the wrong phase raises an error at the point of
 * use rather than registering a callback that could never fire (a client cannot observe server
 * events without networking). {@code events.of(id)} is the unscoped escape hatch.
 */
public final class EventNamespace implements StarlarkValue {
    private final Events events;
    private final String namespace;
    private final Events.Scope scope;

    EventNamespace(Events events, String namespace, Events.Scope scope) {
        this.events = events;
        this.namespace = namespace;
        this.scope = scope;
    }

    // --- Server events (scripts in `server/`). ---

    @StarlarkMethod(
            name = "SERVER_STARTED",
            structField = true,
            doc = "Fires once when the server / world has finished loading.")
    public Event serverStarted() throws EvalException {
        return serverEvent("SERVER_STARTED", "server_started");
    }

    @StarlarkMethod(
            name = "SERVER_TICK",
            structField = true,
            doc = "Fires at the end of every server tick (20 times a second). `ctx` carries no data.")
    public Event serverTick() throws EvalException {
        return serverEvent("SERVER_TICK", "server_tick");
    }

    @StarlarkMethod(
            name = "PLAYER_JOINED",
            structField = true,
            doc = "Fires when a player finishes joining. `ctx.player` is the player.")
    public Event playerJoined() throws EvalException {
        return serverEvent("PLAYER_JOINED", "player_joined");
    }

    @StarlarkMethod(
            name = "PLAYER_LEFT",
            structField = true,
            doc = "Fires when a player disconnects. `ctx.player` is the player.")
    public Event playerLeft() throws EvalException {
        return serverEvent("PLAYER_LEFT", "player_left");
    }

    @StarlarkMethod(
            name = "PLAYER_DEATH",
            structField = true,
            doc = "Fires when a player is about to die. Cancellable (keeps them alive). `ctx.player`, "
                    + "`ctx.source` (the damage type name), and `ctx.amount` (the final blow).")
    public Event playerDeath() throws EvalException {
        return serverEvent("PLAYER_DEATH", "player_death");
    }

    @StarlarkMethod(
            name = "PLAYER_CHAT",
            structField = true,
            doc = "Fires when a player sends a chat message. Cancellable. `ctx.player` and "
                    + "`ctx.message` (editable: reassign `ctx.message` to rewrite the line).")
    public Event playerChat() throws EvalException {
        return serverEvent("PLAYER_CHAT", "player_chat");
    }

    @StarlarkMethod(
            name = "BLOCK_BROKEN",
            structField = true,
            doc = "Fires just before a player breaks a block. Cancellable. `ctx.player`, `ctx.block` "
                    + "(the block id), and `ctx.x` / `ctx.y` / `ctx.z`.")
    public Event blockBroken() throws EvalException {
        return serverEvent("BLOCK_BROKEN", "block_broken");
    }

    @StarlarkMethod(
            name = "BLOCK_PLACED",
            structField = true,
            doc = "Fires just before a player places a block. Cancellable. `ctx.player`, `ctx.block` "
                    + "(the block id), and `ctx.x` / `ctx.y` / `ctx.z`.")
    public Event blockPlaced() throws EvalException {
        return serverEvent("BLOCK_PLACED", "block_placed");
    }

    @StarlarkMethod(
            name = "COMMAND",
            structField = true,
            doc = "Fires before a command runs. Cancellable. `ctx.player` (may be absent for the "
                    + "console), `ctx.command` (editable: reassign to rewrite the command).")
    public Event command() throws EvalException {
        return serverEvent("COMMAND", "command");
    }

    @StarlarkMethod(
            name = "EXPLOSION",
            structField = true,
            doc = "Fires when an explosion goes off (notification). `ctx.x` / `ctx.y` / `ctx.z` and "
                    + "`ctx.power`.")
    public Event explosion() throws EvalException {
        return serverEvent("EXPLOSION", "explosion");
    }

    @StarlarkMethod(
            name = "USE_BLOCK",
            structField = true,
            doc = "Fires when a player right-clicks a block. Cancellable. `ctx.player`, `ctx.block` "
                    + "(the block id), `ctx.x` / `ctx.y` / `ctx.z`, and `ctx.hand` (`main` or `off`).")
    public Event useBlock() throws EvalException {
        return serverEvent("USE_BLOCK", "use_block");
    }

    @StarlarkMethod(
            name = "USE_ITEM",
            structField = true,
            doc = "Fires when a player right-clicks with an item (not aimed at a block). Cancellable. "
                    + "`ctx.player`, `ctx.item` (the held stack), and `ctx.hand` (`main` or `off`).")
    public Event useItem() throws EvalException {
        return serverEvent("USE_ITEM", "use_item");
    }

    @StarlarkMethod(
            name = "USE_ENTITY",
            structField = true,
            doc = "Fires when a player right-clicks an entity. Cancellable. `ctx.player`, `ctx.entity`, "
                    + "and `ctx.hand` (`main` or `off`).")
    public Event useEntity() throws EvalException {
        return serverEvent("USE_ENTITY", "use_entity");
    }

    @StarlarkMethod(
            name = "ATTACK_ENTITY",
            structField = true,
            doc = "Fires when a player left-clicks (attacks) an entity. Cancellable. `ctx.player`, "
                    + "`ctx.entity`, and `ctx.hand` (`main` or `off`).")
    public Event attackEntity() throws EvalException {
        return serverEvent("ATTACK_ENTITY", "attack_entity");
    }

    @StarlarkMethod(
            name = "ENTITY_DEATH",
            structField = true,
            doc = "Fires when a non-player living entity is about to die. Cancellable (keeps it alive). "
                    + "`ctx.entity`, `ctx.source` (the damage type name), `ctx.amount`, and `ctx.attacker` "
                    + "(if any). For players, use `PLAYER_DEATH` instead.")
    public Event entityDeath() throws EvalException {
        return serverEvent("ENTITY_DEATH", "entity_death");
    }

    @StarlarkMethod(
            name = "ENTITY_DAMAGE",
            structField = true,
            doc = "Fires when a living entity (a mob or a player) is about to take damage. Cancellable "
                    + "(prevents it). `ctx.entity`, `ctx.source` (the damage type name), `ctx.amount`, and "
                    + "`ctx.player` when the victim is a player.")
    public Event entityDamage() throws EvalException {
        return serverEvent("ENTITY_DAMAGE", "entity_damage");
    }

    @StarlarkMethod(
            name = "PLAYER_RESPAWN",
            structField = true,
            doc = "Fires after a player respawns. `ctx.player` is the respawned player.")
    public Event playerRespawn() throws EvalException {
        return serverEvent("PLAYER_RESPAWN", "player_respawn");
    }

    @StarlarkMethod(
            name = "DIMENSION_CHANGE",
            structField = true,
            doc = "Fires after a player moves to another dimension. `ctx.player`, `ctx.origin` and "
                    + "`ctx.destination` (the worlds moved from and to).")
    public Event dimensionChange() throws EvalException {
        return serverEvent("DIMENSION_CHANGE", "dimension_change");
    }

    @StarlarkMethod(
            name = "PLAYER_TICK",
            structField = true,
            doc = "Fires once per online player every server tick (20 times a second). `ctx.player` is "
                    + "the player. Only fires while it has a listener, so an idle server pays nothing.")
    public Event playerTick() throws EvalException {
        return serverEvent("PLAYER_TICK", "player_tick");
    }

    // --- Client events (scripts in `client/`). These run on the player's own machine. ---

    @StarlarkMethod(
            name = "CLIENT_STARTED",
            structField = true,
            doc = "Fires once when the game client has finished starting up. `ctx` carries no data. "
                    + "Client scripts only.")
    public Event clientStarted() throws EvalException {
        return clientEvent("CLIENT_STARTED", "client_started");
    }

    @StarlarkMethod(
            name = "CLIENT_STOPPING",
            structField = true,
            doc = "Fires once as the game client is shutting down. `ctx` carries no data. Client scripts only.")
    public Event clientStopping() throws EvalException {
        return clientEvent("CLIENT_STOPPING", "client_stopping");
    }

    @StarlarkMethod(
            name = "CLIENT_TICK",
            structField = true,
            doc = "Fires at the end of every client tick (20 times a second). `ctx` carries no data. "
                    + "Client scripts only.")
    public Event clientTick() throws EvalException {
        return clientEvent("CLIENT_TICK", "client_tick");
    }

    @StarlarkMethod(
            name = "ITEM_TOOLTIP",
            structField = true,
            doc = "Fires while an item's tooltip is being built. `ctx.item` is the stack. Append extra "
                    + "lines by reassigning `ctx.lines` (a list of strings or `text(...)` components), "
                    + "e.g. `ctx.lines = ctx.lines + [\"Hello\"]`. Client scripts only.")
    public Event itemTooltip() throws EvalException {
        return clientEvent("ITEM_TOOLTIP", "item_tooltip");
    }

    @StarlarkMethod(
            name = "CLIENT_CHAT_RECEIVED",
            structField = true,
            doc = "Fires when the client receives a chat or system message. Cancellable (hides the line). "
                    + "`ctx.message` is the text; reassigning it rewrites system messages (signed player "
                    + "chat can only be cancelled, not edited). Client scripts only.")
    public Event clientChatReceived() throws EvalException {
        return clientEvent("CLIENT_CHAT_RECEIVED", "client_chat_received");
    }

    @StarlarkMethod(
            name = "CLIENT_CHAT_SENT",
            structField = true,
            doc = "Fires just before the player sends a chat message. Cancellable (stops it). "
                    + "`ctx.message` is the text; reassign it to rewrite the outgoing line. Client scripts only.")
    public Event clientChatSent() throws EvalException {
        return clientEvent("CLIENT_CHAT_SENT", "client_chat_sent");
    }

    // --- scope enforcement ---

    private Event serverEvent(String constant, String path) throws EvalException {
        return scoped(Events.Scope.SERVER, constant, path);
    }

    private Event clientEvent(String constant, String path) throws EvalException {
        return scoped(Events.Scope.CLIENT, constant, path);
    }

    private Event scoped(Events.Scope required, String constant, String path) throws EvalException {
        if (scope != required) {
            throw new EvalException(constant + " is a " + required.folder() + " event; register it from a "
                    + required.folder() + "/ script (this is a " + scope.folder() + " script). "
                    + "A client cannot receive server events without networking.");
        }
        return new Event(events, namespace + ":" + path);
    }
}
