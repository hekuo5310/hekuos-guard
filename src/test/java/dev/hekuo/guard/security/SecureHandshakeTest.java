package dev.hekuo.guard.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureHandshakeTest {
    @Test
    void signedChallengeAndEncryptedProofRoundTrip() throws Exception {
        KeyPair serverSigning = SecureHandshake.ed25519KeyPair();
        KeyPair serverAgreement = SecureHandshake.x25519KeyPair();
        KeyPair clientAgreement = SecureHandshake.x25519KeyPair();
        byte[] nonce = SecureHandshake.nonce();
        byte[] challenge = SecureHandshake.challengeMessage(nonce, serverAgreement.getPublic().getEncoded());
        byte[] signature = SecureHandshake.sign(serverSigning.getPrivate(), challenge);
        assertTrue(SecureHandshake.verify(serverSigning.getPublic().getEncoded(), challenge, signature));

        byte[] plaintext = "integrity-proof".getBytes();
        byte[] iv = SecureHandshake.iv();
        byte[] ciphertext = SecureHandshake.encrypt(clientAgreement.getPrivate(), serverAgreement.getPublic().getEncoded(), nonce, iv, plaintext);
        assertArrayEquals(plaintext, SecureHandshake.decrypt(serverAgreement.getPrivate(), clientAgreement.getPublic().getEncoded(), nonce, iv, ciphertext));

        ciphertext[0] ^= 1;
        assertThrows(Exception.class, () -> SecureHandshake.decrypt(serverAgreement.getPrivate(), clientAgreement.getPublic().getEncoded(), nonce, iv, ciphertext));
    }

    @Test
    void signaturesCannotBeReplayedAgainstAnotherChallenge() throws Exception {
        KeyPair signing = SecureHandshake.ed25519KeyPair();
        KeyPair agreement = SecureHandshake.x25519KeyPair();
        byte[] first = SecureHandshake.challengeMessage(SecureHandshake.nonce(), agreement.getPublic().getEncoded());
        byte[] second = SecureHandshake.challengeMessage(SecureHandshake.nonce(), agreement.getPublic().getEncoded());
        byte[] signature = SecureHandshake.sign(signing.getPrivate(), first);
        assertFalse(SecureHandshake.verify(signing.getPublic().getEncoded(), second, signature));
    }
}
