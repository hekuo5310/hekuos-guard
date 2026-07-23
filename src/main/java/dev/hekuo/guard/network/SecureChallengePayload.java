package dev.hekuo.guard.network;

import dev.hekuo.guard.HekuosGuard;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Signed server challenge for the optional encrypted client proof. */
public record SecureChallengePayload(byte[] signingKey, byte[] agreementKey, byte[] nonce, byte[] signature) implements CustomPayload {
    private static final int MAX_FIELD_LENGTH = 512;
    public static final Id<SecureChallengePayload> ID = new Id<>(Identifier.of(HekuosGuard.MOD_ID, "secure_challenge"));
    public static final PacketCodec<RegistryByteBuf, SecureChallengePayload> CODEC = PacketCodec.of(
            (payload, buffer) -> { write(buffer, payload.signingKey); write(buffer, payload.agreementKey); write(buffer, payload.nonce); write(buffer, payload.signature); },
            buffer -> new SecureChallengePayload(read(buffer), read(buffer), read(buffer), read(buffer)));
    private static void write(RegistryByteBuf buffer, byte[] value) { buffer.writeVarInt(value.length); buffer.writeBytes(value); }
    private static byte[] read(RegistryByteBuf buffer) { int length = buffer.readVarInt(); if (length < 1 || length > MAX_FIELD_LENGTH) throw new IllegalArgumentException("invalid secure challenge"); byte[] value = new byte[length]; buffer.readBytes(value); return value; }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
