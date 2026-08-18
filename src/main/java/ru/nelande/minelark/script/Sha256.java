package ru.nelande.minelark.script;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A tiny SHA-256 hex helper. Used to fingerprint pushed script bodies (so the client can verify the
 * integrity of what it received against the manifest, and cache by content) and to derive the stable
 * bundle hash that drives hot-push change detection. MC-agnostic, unit-testable.
 */
public final class Sha256 {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Sha256() {
    }

    /** The lowercase hex SHA-256 of {@code text}'s UTF-8 bytes. */
    public static String hex(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** The lowercase hex SHA-256 of {@code bytes}. */
    public static String hex(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but missing", e);   // never on a standard JRE
        }
        byte[] hash = digest.digest(bytes);
        char[] out = new char[hash.length * 2];
        for (int i = 0; i < hash.length; i++) {
            int b = hash[i] & 0xff;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0f];
        }
        return new String(out);
    }
}
