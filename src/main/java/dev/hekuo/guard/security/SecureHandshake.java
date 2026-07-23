package dev.hekuo.guard.security;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/** Cryptographic primitives for the optional, per-connection client proof. */
public final class SecureHandshake {
    public static final int NONCE_LENGTH = 32;
    public static final int HASH_LENGTH = 32;
    public static final int GCM_IV_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureHandshake() { }

    public static KeyPair ed25519KeyPair() throws GeneralSecurityException { return KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
    public static KeyPair x25519KeyPair() throws GeneralSecurityException { return KeyPairGenerator.getInstance("X25519").generateKeyPair(); }
    public static byte[] nonce() { byte[] value = new byte[NONCE_LENGTH]; RANDOM.nextBytes(value); return value; }
    public static byte[] iv() { byte[] value = new byte[GCM_IV_LENGTH]; RANDOM.nextBytes(value); return value; }

    public static byte[] sign(PrivateKey key, byte[] message) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key);
        signature.update(message);
        return signature.sign();
    }

    public static boolean verify(byte[] encodedKey, byte[] message, byte[] signatureBytes) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(decodeEd25519(encodedKey));
        signature.update(message);
        return signature.verify(signatureBytes);
    }

    public static byte[] encrypt(PrivateKey ownKey, byte[] peerKey, byte[] nonce, byte[] iv, byte[] plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveAesKey(ownKey, peerKey, nonce), new GCMParameterSpec(128, iv));
        cipher.updateAAD(nonce);
        return cipher.doFinal(plaintext);
    }

    public static byte[] decrypt(PrivateKey ownKey, byte[] peerKey, byte[] nonce, byte[] iv, byte[] ciphertext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveAesKey(ownKey, peerKey, nonce), new GCMParameterSpec(128, iv));
        cipher.updateAAD(nonce);
        return cipher.doFinal(ciphertext);
    }

    public static byte[] challengeMessage(byte[] nonce, byte[] agreementKey) { return concat(nonce, agreementKey); }
    public static byte[] proofMessage(byte[] nonce, byte[] serverAgreementKey, byte[] clientAgreementKey, byte[] integrityHash) {
        return concat(nonce, serverAgreementKey, clientAgreementKey, integrityHash);
    }
    public static byte[] encodeIntegrity(byte[] clientSigningKey, byte[] integrityHash, byte[] signature) {
        if (integrityHash.length != HASH_LENGTH) throw new IllegalArgumentException("invalid integrity hash");
        ByteBuffer buffer = ByteBuffer.allocate(4 + clientSigningKey.length + HASH_LENGTH + 4 + signature.length);
        buffer.putInt(clientSigningKey.length).put(clientSigningKey).put(integrityHash).putInt(signature.length).put(signature);
        return buffer.array();
    }
    public static Integrity decodeIntegrity(byte[] value) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            int keyLength = buffer.getInt();
            if (keyLength < 1 || keyLength > 512 || buffer.remaining() < keyLength + HASH_LENGTH + 4) throw new IllegalArgumentException("invalid integrity proof");
            byte[] key = new byte[keyLength]; buffer.get(key);
            byte[] hash = new byte[HASH_LENGTH]; buffer.get(hash);
            int signatureLength = buffer.getInt();
            if (signatureLength < 1 || signatureLength > 512 || buffer.remaining() != signatureLength) throw new IllegalArgumentException("invalid integrity proof");
            byte[] signature = new byte[signatureLength]; buffer.get(signature);
            return new Integrity(key, hash, signature);
        } catch (RuntimeException exception) { throw new IllegalArgumentException("invalid integrity proof", exception); }
    }
    public static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item));
        return result.toString();
    }
    public static byte[] sha256(byte[] value) throws GeneralSecurityException { return MessageDigest.getInstance("SHA-256").digest(value); }

    private static SecretKey deriveAesKey(PrivateKey ownKey, byte[] peerKey, byte[] nonce) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(ownKey);
        agreement.doPhase(decodeX25519(peerKey), true);
        return new SecretKeySpec(sha256(concat(agreement.generateSecret(), nonce)), "AES");
    }
    private static PublicKey decodeEd25519(byte[] encoded) throws GeneralSecurityException { return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded)); }
    private static PublicKey decodeX25519(byte[] encoded) throws GeneralSecurityException { return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(encoded)); }
    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) buffer.put(part);
        return buffer.array();
    }

    public record Integrity(byte[] clientSigningKey, byte[] integrityHash, byte[] signature) { }
}
