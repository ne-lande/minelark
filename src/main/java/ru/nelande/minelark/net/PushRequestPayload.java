package ru.nelande.minelark.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import ru.nelande.minelark.Minelark;

/**
 * Client-to-server: the client asks for the bodies of specific pushed scripts (the ones it does not
 * already have cached at the manifest's hash). {@code names} is a newline-separated list of manifest
 * file names; the server replies with one {@link PushDeliverPayload} per name it recognises.
 */
public record PushRequestPayload(String names) implements CustomPayload {
    public static final CustomPayload.Id<PushRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Minelark.MOD_ID, "push_request"));

    public static final PacketCodec<RegistryByteBuf, PushRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(0x10000), PushRequestPayload::names,
            PushRequestPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
