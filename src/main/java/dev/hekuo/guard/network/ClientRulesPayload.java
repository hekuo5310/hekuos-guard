package dev.hekuo.guard.network;

import dev.hekuo.guard.HekuosGuard;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Server-to-client list of Fabric mod ids the server forbids. */
public record ClientRulesPayload(List<String> blockedModIds) implements CustomPayload {
    private static final int MAX_RULES = 256;
    private static final int MAX_MOD_ID_LENGTH = 128;
    public static final Id<ClientRulesPayload> ID = new Id<>(Identifier.of(HekuosGuard.MOD_ID, "client_rules"));
    public static final PacketCodec<RegistryByteBuf, ClientRulesPayload> CODEC = PacketCodec.of(
            (payload, buffer) -> {
                buffer.writeVarInt(payload.blockedModIds.size());
                for (String modId : payload.blockedModIds) buffer.writeString(modId, MAX_MOD_ID_LENGTH);
            },
            buffer -> {
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_RULES) throw new IllegalArgumentException("invalid rule count");
                List<String> rules = new ArrayList<>(count);
                for (int index = 0; index < count; index++) rules.add(buffer.readString(MAX_MOD_ID_LENGTH));
                return new ClientRulesPayload(List.copyOf(rules));
            });

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
