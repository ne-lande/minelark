package ru.nelande.minelark.script;

import java.util.Map;

/**
 * A server's pushable client scripts: the {@link PushManifest} it offers (file hashes + requested
 * capabilities + bundle fingerprint) and the file bodies keyed by their manifest name. The manifest
 * goes to every connecting client; a body is only handed out when the client asks for it by name
 * (after consenting). MC-agnostic.
 */
public record PushBundle(PushManifest manifest, Map<String, String> bodies) {

    /** An offer with no files - what a missing folder or an all-unflagged folder produces. */
    public static final PushBundle EMPTY =
            new PushBundle(PushManifest.of(java.util.List.of(), java.util.Set.of()), Map.of());

    public PushBundle {
        bodies = Map.copyOf(bodies);
    }

    public boolean isEmpty() {
        return manifest.entries().isEmpty();
    }

    /** The stable fingerprint of this offer; drives client-side change detection for hot-push. */
    public String bundleHash() {
        return manifest.bundleHash();
    }
}
