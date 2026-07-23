package dev.hekuo.guard.network;

import dev.hekuo.guard.HekuosGuard;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Encrypted response to a {@link SecureChallengePayload}. */
public record SecureResponsePayload(byte[] agreementKey, byte[] iv, byte[] ciphertext) implements CustomPayload {
    private static final int MAX_FIELD_LENGTH = 4096;
    public static final Id<SecureResponsePayload> ID = new Id<>(Identifier.of(HekuosGuard.MOD_ID, "secure_response"));
    public static final PacketCodec<RegistryByteBuf, SecureResponsePayload> CODEC = PacketCodec.of(
            (payload, buffer) -> { write(buffer, payload.agreementKey); write(buffer, payload.iv); write(buffer, payload.ciphertext); },
            buffer -> new SecureResponsePayload(read(buffer), read(buffer), read(buffer)));
    private static void write(RegistryByteBuf buffer, byte[] value) { buffer.writeVarInt(value.length); buffer.writeBytes(value); }
    private static byte[] read(RegistryByteBuf buffer) { int length = buffer.readVarInt(); if (length < 1 || length > MAX_FIELD_LENGTH) throw new IllegalArgumentException("invalid secure response"); byte[] value = new byte[length]; buffer.readBytes(value); return value; }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
