package ru.nelande.minelark.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import ru.nelande.minelark.Minelark;

/**
 * Server-to-client: the body of one requested pushed script. The client verifies the body's SHA-256
 * against the {@link ru.nelande.minelark.script.PushManifest} entry before writing or running it, so
 * a corrupted or spoofed body is dropped.
 */
public record PushDeliverPayload(String name, String body) implements CustomPayload {
    public static final CustomPayload.Id<PushDeliverPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Minelark.MOD_ID, "push_deliver"));

    public static final PacketCodec<RegistryByteBuf, PushDeliverPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(0x400), PushDeliverPayload::name,
            PacketCodecs.string(0x100000), PushDeliverPayload::body,
            PushDeliverPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
