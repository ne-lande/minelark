package ru.nelande.minelark.script;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A capability a server may <b>request</b> for the client scripts it pushes, and that the client's
 * policy may <b>grant</b>. Only the potentially sensitive namespaces are negotiated: {@code events},
 * {@code text}, {@code log}, and the prelude are always present in a pushed script's sandbox because
 * they are harmless (local, immutable, no reach off the client).
 *
 * <p>The effective set a pushed script runs with is the intersection of what the server requested and
 * what the client's {@link RemoteScriptPolicy} allows. The secure default ({@link #VISUAL_DEFAULTS})
 * is visual-only: it excludes {@link #NET} (data exchange with the server) and {@link #CHAT} (which
 * could run commands or send chat as the player - the one real remote-code-execution vector).
 *
 * <p>MC-agnostic and unit-testable; the token strings are the on-the-wire / on-disk names.
 */
public enum Capability {
    /** Draw on-screen HUD graphics ({@code hud} namespace). */
    HUD("hud"),
    /** Add F3 debug-overlay lines ({@code debug} namespace). */
    DEBUG("debug"),
    /** Exchange JSON with the server over named channels ({@code net} namespace). Not granted by default. */
    NET("net"),
    /** Read the local player and world and show local messages ({@code client.player/world/show_message}). */
    CLIENT_READ("client"),
    /** Send chat and run commands as the player ({@code client.send_chat}). Not granted by default. */
    CHAT("chat"),
    /** Discover loaded mods ({@code mods} namespace). */
    MODS("mods"),
    /** Query the registries ({@code registry} namespace). */
    REGISTRY("registry");

    /** The secure default the client grants without asking: visual + read-only, no {@code net}/{@code chat}. */
    public static final Set<Capability> VISUAL_DEFAULTS =
            EnumSet.of(HUD, DEBUG, CLIENT_READ, MODS, REGISTRY);

    private final String token;

    Capability(String token) {
        this.token = token;
    }

    /** The stable lowercase name used in JSON manifests and the policy file. */
    public String token() {
        return token;
    }

    /** Parses a token back to its capability, or empty if it is not a known one (forward-compatible). */
    public static Optional<Capability> fromToken(String token) {
        for (Capability capability : values()) {
            if (capability.token.equals(token)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }

    /** Parses a list of tokens, silently dropping any that are unknown (so new tokens do not break old clients). */
    public static Set<Capability> parse(Iterable<String> tokens) {
        Set<Capability> result = EnumSet.noneOf(Capability.class);
        for (String token : tokens) {
            fromToken(token.trim()).ifPresent(result::add);
        }
        return result;
    }

    /** Renders a set of capabilities to a sorted (by declaration order) list of tokens. */
    public static List<String> tokens(Set<Capability> capabilities) {
        List<String> result = new ArrayList<>();
        for (Capability capability : values()) {   // stable order regardless of the set's iteration order
            if (capabilities.contains(capability)) {
                result.add(capability.token);
            }
        }
        return result;
    }
}
