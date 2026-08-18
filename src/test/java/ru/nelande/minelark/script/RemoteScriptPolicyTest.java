package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 tests for {@link RemoteScriptPolicy}: secure defaults, the accept/decline/prompt decision,
 * trusted/blocked sources, remembered choices, capability capping, and disk persistence.
 */
class RemoteScriptPolicyTest {

    private static final String SRV = "play.example.com:25565";
    private static final Set<Capability> WANTS_NET = Set.of(Capability.HUD, Capability.NET);

    @Test
    void secureDefaults() {
        RemoteScriptPolicy policy = RemoteScriptPolicy.inMemory();
        assertTrue(policy.enabled());
        assertEquals(Capability.VISUAL_DEFAULTS, policy.defaultAllow());
        assertFalse(policy.defaultAllow().contains(Capability.NET));
        assertFalse(policy.defaultAllow().contains(Capability.CHAT));
    }

    @Test
    void unknownSourcePrompts() {
        assertEquals(RemoteScriptPolicy.Decision.PROMPT,
                RemoteScriptPolicy.inMemory().decide(SRV, WANTS_NET));
    }

    @Test
    void rememberedChoicesAreHonoured() {
        RemoteScriptPolicy policy = RemoteScriptPolicy.inMemory();
        policy.remember(SRV, true);
        assertEquals(RemoteScriptPolicy.Decision.ACCEPT, policy.decide(SRV, WANTS_NET));
        policy.remember(SRV, false);
        assertEquals(RemoteScriptPolicy.Decision.DECLINE, policy.decide(SRV, WANTS_NET));
    }

    @Test
    void trustedSourceSkipsThePrompt() {
        RemoteScriptPolicy policy = RemoteScriptPolicy.inMemory();
        policy.trust(SRV);
        assertEquals(RemoteScriptPolicy.Decision.ACCEPT, policy.decide(SRV, WANTS_NET));
    }

    @Test
    void blockedSourceIsDeclined() {
        RemoteScriptPolicy policy = RemoteScriptPolicy.inMemory();
        policy.block(SRV);
        assertEquals(RemoteScriptPolicy.Decision.DECLINE, policy.decide(SRV, WANTS_NET));
    }

    @Test
    void grantedIsCappedByThePolicy() {
        RemoteScriptPolicy policy = RemoteScriptPolicy.inMemory();
        // The server asked for net too, but the visual default withholds it.
        assertEquals(Set.of(Capability.HUD), policy.granted(WANTS_NET));
    }

    @Test
    void disabledPolicyDeclinesEverything() {
        RemoteScriptPolicy policy = RemoteScriptPolicy.load(writeEnabledFalse());
        assertEquals(RemoteScriptPolicy.Decision.DECLINE, policy.decide(SRV, WANTS_NET));
    }

    @Test
    void persistsAndReloads(@TempDir Path dir) {
        Path file = dir.resolve("remote_policy.json");
        RemoteScriptPolicy policy = RemoteScriptPolicy.load(file);
        policy.trust("192.168.0.10");
        policy.remember(SRV, false);

        RemoteScriptPolicy reloaded = RemoteScriptPolicy.load(file);
        assertEquals(RemoteScriptPolicy.Decision.ACCEPT, reloaded.decide("192.168.0.10", WANTS_NET));
        assertEquals(RemoteScriptPolicy.Decision.DECLINE, reloaded.decide(SRV, WANTS_NET));
    }

    /** Writes a policy file with the feature switched off, returning its path. */
    private static Path writeEnabledFalse() {
        try {
            Path file = java.nio.file.Files.createTempFile("policy", ".json");
            java.nio.file.Files.writeString(file, "{\"enabled\": false}");
            return file;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
