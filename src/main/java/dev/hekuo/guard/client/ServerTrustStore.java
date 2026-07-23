package dev.hekuo.guard.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;

/** Trust-on-first-use store for the persistent offline-server signing key. */
final class ServerTrustStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hekuos_guard_known_servers.json");
    private ServerTrustStore() { }

    static synchronized boolean trust(MinecraftClient client, byte[] signingKey) throws IOException {
        Files.createDirectories(PATH.getParent());
        Map<String, String> trusted = new HashMap<>();
        if (Files.exists(PATH)) {
            JsonObject saved = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), JsonObject.class);
            if (saved != null) saved.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) trusted.put(entry.getKey(), entry.getValue().getAsString());
            });
        }
        String server = client.getCurrentServerEntry() == null ? "integrated" : client.getCurrentServerEntry().address;
        String encoded = Base64.getEncoder().encodeToString(signingKey);
        String known = trusted.get(server);
        if (known != null) return known.equals(encoded);
        trusted.put(server, encoded);
        Files.writeString(PATH, GSON.toJson(trusted), StandardCharsets.UTF_8);
        return true;
    }
}
