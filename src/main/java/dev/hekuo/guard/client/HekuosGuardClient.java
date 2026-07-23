package dev.hekuo.guard.client;

import dev.hekuo.guard.HekuosGuard;
import dev.hekuo.guard.network.ClientModReportPayload;
import dev.hekuo.guard.network.ClientRulesPayload;
import dev.hekuo.guard.network.SecureChallengePayload;
import dev.hekuo.guard.network.SecureResponsePayload;
import dev.hekuo.guard.security.SecureHandshake;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

/** Optional client companion: it reports only rules explicitly sent by this server. */
public final class HekuosGuardClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ClientRulesPayload.ID, (payload, context) -> context.client().execute(() -> {
            Set<String> installed = FabricLoader.getInstance().getAllMods().stream()
                    .map(container -> container.getMetadata().getId().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            for (String rule : payload.blockedModIds()) {
                String normalized = rule.toLowerCase(Locale.ROOT);
                if (installed.contains(normalized)) ClientPlayNetworking.send(new ClientModReportPayload(normalized));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(SecureChallengePayload.ID, (payload, context) -> context.client().execute(() -> {
            try {
                if (!ServerTrustStore.trust(context.client(), payload.signingKey())) {
                    HekuosGuard.LOGGER.warn("Rejected secure challenge because this server's signing key changed");
                    return;
                }
                if (!SecureHandshake.verify(payload.signingKey(), SecureHandshake.challengeMessage(payload.nonce(), payload.agreementKey()), payload.signature())) {
                    HekuosGuard.LOGGER.warn("Rejected invalid server secure challenge signature");
                    return;
                }
                KeyPair agreement = SecureHandshake.x25519KeyPair();
                KeyPair signing = SecureHandshake.ed25519KeyPair();
                byte[] integrity = ownJarHash();
                byte[] proof = SecureHandshake.proofMessage(payload.nonce(), payload.agreementKey(), agreement.getPublic().getEncoded(), integrity);
                byte[] signature = SecureHandshake.sign(signing.getPrivate(), proof);
                byte[] plaintext = SecureHandshake.encodeIntegrity(signing.getPublic().getEncoded(), integrity, signature);
                byte[] iv = SecureHandshake.iv();
                byte[] ciphertext = SecureHandshake.encrypt(agreement.getPrivate(), payload.agreementKey(), payload.nonce(), iv, plaintext);
                ClientPlayNetworking.send(new SecureResponsePayload(agreement.getPublic().getEncoded(), iv, ciphertext));
            } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
                HekuosGuard.LOGGER.warn("Could not complete secure handshake", exception);
            }
        }));
        HekuosGuard.LOGGER.info("hekuo's guard client companion initialized");
    }

    private static byte[] ownJarHash() throws IOException, GeneralSecurityException {
        try {
            Path source = Path.of(HekuosGuardClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return SecureHandshake.sha256(Files.readAllBytes(source));
        } catch (java.net.URISyntaxException exception) {
            throw new IOException("invalid client code source", exception);
        }
    }
}
