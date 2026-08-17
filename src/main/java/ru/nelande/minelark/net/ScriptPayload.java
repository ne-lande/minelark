package ru.nelande.minelark.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import ru.nelande.minelark.Minelark;

/**
 * The single custom packet Minelark's {@code net} namespace rides on, both directions: a channel name
 * (the script-chosen topic) and a JSON string (the encoded payload). Registered on the play S2C and
 * C2S channels in {@link Minelark#onInitialize()}; sent/received by the server and client adapters.
 */
public record ScriptPayload(String channel, String data) implements CustomPayload {
    public static final CustomPayload.Id<ScriptPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Minelark.MOD_ID, "script"));

    public static final PacketCodec<RegistryByteBuf, ScriptPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ScriptPayload::channel,
            PacketCodecs.STRING, ScriptPayload::data,
            ScriptPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
