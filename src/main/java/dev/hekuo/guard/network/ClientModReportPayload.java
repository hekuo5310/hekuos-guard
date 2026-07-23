package dev.hekuo.guard.network;

import dev.hekuo.guard.HekuosGuard;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client-to-server report for one locally installed, server-forbidden Fabric mod id. */
public record ClientModReportPayload(String modId) implements CustomPayload {
    private static final int MAX_MOD_ID_LENGTH = 128;
    public static final Id<ClientModReportPayload> ID = new Id<>(Identifier.of(HekuosGuard.MOD_ID, "client_mod_report"));
    public static final PacketCodec<RegistryByteBuf, ClientModReportPayload> CODEC = PacketCodec.of(
            (payload, buffer) -> buffer.writeString(payload.modId, MAX_MOD_ID_LENGTH),
            buffer -> new ClientModReportPayload(buffer.readString(MAX_MOD_ID_LENGTH)));

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
