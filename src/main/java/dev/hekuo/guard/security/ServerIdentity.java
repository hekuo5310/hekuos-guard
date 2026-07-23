package dev.hekuo.guard.security;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Persistent server signing identity. Keep the generated private key out of version control. */
public final class ServerIdentity {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("hekuos_guard_identity.json");
    private KeyPair keyPair;

    public synchronized KeyPair loadOrCreate() throws IOException, GeneralSecurityException {
        if (keyPair != null) return keyPair;
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) {
            StoredIdentity stored = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), StoredIdentity.class);
            if (stored == null || stored.privateKey == null || stored.publicKey == null) throw new IOException("invalid server identity file");
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(stored.privateKey)));
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(stored.publicKey)));
            return keyPair = new KeyPair(publicKey, privateKey);
        }
        keyPair = SecureHandshake.ed25519KeyPair();
        StoredIdentity stored = new StoredIdentity(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()), Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        Files.writeString(path, GSON.toJson(stored), StandardCharsets.UTF_8);
        return keyPair;
    }

    private record StoredIdentity(String privateKey, String publicKey) { }
}
