package ru.nelande.minelark.script;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-1 tests for {@link PushManifest}: JSON round-trip and a stable, order-independent bundle hash. */
class PushManifestTest {

    private static PushManifest sample() {
        return PushManifest.of(
                List.of(
                        new PushManifest.Entry("a.star", Sha256.hex("body-a"), 6),
                        new PushManifest.Entry("lib/b.star", Sha256.hex("body-b"), 6)),
                Set.of(Capability.HUD, Capability.NET));
    }

    @Test
    void jsonRoundTrips() {
        PushManifest manifest = sample();
        PushManifest back = PushManifest.fromJsonString(manifest.toJsonString());
        assertEquals(manifest.bundleHash(), back.bundleHash());
        assertEquals(manifest.entries(), back.entries());
        assertEquals(manifest.requested(), back.requested());
    }

    @Test
    void bundleHashIsOrderIndependentButContentSensitive() {
        PushManifest a = PushManifest.of(
                List.of(
                        new PushManifest.Entry("a.star", "aaa", 1),
                        new PushManifest.Entry("b.star", "bbb", 1)),
                Set.of(Capability.HUD));
        PushManifest reordered = PushManifest.of(
                List.of(
                        new PushManifest.Entry("b.star", "bbb", 1),
                        new PushManifest.Entry("a.star", "aaa", 1)),
                Set.of(Capability.HUD));
        assertEquals(a.bundleHash(), reordered.bundleHash(), "hash must not depend on entry order");

        PushManifest changedContent = PushManifest.of(
                List.of(
                        new PushManifest.Entry("a.star", "aaa", 1),
                        new PushManifest.Entry("b.star", "different", 1)),
                Set.of(Capability.HUD));
        assertNotEquals(a.bundleHash(), changedContent.bundleHash(), "content change must change the hash");

        PushManifest changedCaps = PushManifest.of(
                List.of(
                        new PushManifest.Entry("a.star", "aaa", 1),
                        new PushManifest.Entry("b.star", "bbb", 1)),
                Set.of(Capability.HUD, Capability.NET));
        assertNotEquals(a.bundleHash(), changedCaps.bundleHash(), "capability change must change the hash");
    }

    @Test
    void unknownCapabilityTokensAreDropped() {
        String json = """
                {"bundle_hash":"x","capabilities":["hud","telepathy"],"files":[]}
                """;
        PushManifest manifest = PushManifest.fromJsonString(json);
        assertEquals(Set.of(Capability.HUD), manifest.requested());
        assertTrue(manifest.entries().isEmpty());
    }
}
