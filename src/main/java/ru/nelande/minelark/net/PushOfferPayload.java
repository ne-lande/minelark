package ru.nelande.minelark.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import ru.nelande.minelark.Minelark;

/**
 * Server-to-client: the offer that starts script propagation. Carries the {@link ru.nelande.minelark.script.PushManifest}
 * as JSON (file names + SHA-256 hashes + requested capabilities + a bundle fingerprint) - but no
 * script bodies. The client decides, from its own policy, whether to run them, and only then requests
 * the bodies it needs ({@link PushRequestPayload}).
 */
public record PushOfferPayload(String manifest) implements CustomPayload {
    public static final CustomPayload.Id<PushOfferPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Minelark.MOD_ID, "push_offer"));

    /** Generous cap: a manifest of many hashed files is far larger than the default string limit. */
    public static final PacketCodec<RegistryByteBuf, PushOfferPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(0x100000), PushOfferPayload::manifest,
            PushOfferPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
