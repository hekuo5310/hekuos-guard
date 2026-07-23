package dev.hekuo.guard.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hekuo.guard.HekuosGuard;
import dev.hekuo.guard.config.GuardConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

/** Checks GitHub releases and stages a verified replacement JAR for the next restart. */
public final class UpdateManager {
    private static final long MAX_JAR_BYTES = 64L * 1024L * 1024L;
    private final GuardConfig config;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicReference<String> status = new AtomicReference<>("not checked");

    public UpdateManager(GuardConfig config) { this.config = config; }
    public String status() { return status.get(); }
    public void checkAtStartup() {
        GuardConfig.Updater settings = config.get().updater;
        if (settings.enabled && settings.checkOnStartup) check(settings.autoDownload);
    }
    public void check(boolean download) {
        GuardConfig.Updater settings = config.get().updater;
        if (!settings.enabled) { status.set("disabled by configuration"); return; }
        status.set("checking GitHub release");
        CompletableFuture.runAsync(() -> checkNow(download, settings));
    }

    private void checkNow(boolean download, GuardConfig.Updater settings) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + settings.githubRepository + "/releases/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "hekuos-guard-updater")
                    .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IOException("GitHub API returned HTTP " + response.statusCode());
            JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
            String remoteVersion = release.get("tag_name").getAsString().replaceFirst("^v", "");
            String currentVersion = FabricLoader.getInstance().getModContainer(HekuosGuard.MOD_ID).orElseThrow()
                    .getMetadata().getVersion().getFriendlyString();
            if (compareVersions(remoteVersion, currentVersion) <= 0) {
                status.set("up to date (" + currentVersion + ")");
                return;
            }
            JsonObject asset = release.getAsJsonArray("assets").asList().stream().map(element -> element.getAsJsonObject())
                    .filter(item -> item.get("name").getAsString().endsWith(".jar") && !item.get("name").getAsString().contains("-sources"))
                    .min(Comparator.comparing(item -> item.get("name").getAsString())).orElseThrow(() -> new IOException("release has no mod JAR"));
            String name = asset.get("name").getAsString();
            if (!download) { status.set("update available: " + remoteVersion + " (" + name + ")"); return; }
            long size = asset.get("size").getAsLong();
            if (size < 1 || size > MAX_JAR_BYTES) throw new IOException("unexpected JAR size: " + size);
            Path directory = FabricLoader.getInstance().getConfigDir().resolve("hekuos_guard").resolve("updates");
            Files.createDirectories(directory);
            Path temporary = directory.resolve(name + ".part");
            Path destination = directory.resolve(name);
            HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(asset.get("browser_download_url").getAsString()))
                    .header("User-Agent", "hekuos-guard-updater").timeout(Duration.ofSeconds(settings.requestTimeoutSeconds)).GET().build();
            HttpResponse<Path> downloadResponse = client.send(downloadRequest, HttpResponse.BodyHandlers.ofFile(temporary));
            if (downloadResponse.statusCode() != 200) throw new IOException("download returned HTTP " + downloadResponse.statusCode());
            verifyJar(temporary, remoteVersion);
            try { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            status.set("staged " + name + "; replace the current JAR and restart the server");
            HekuosGuard.LOGGER.info("Update {} staged at {}; replace the current JAR and restart", remoteVersion, destination);
        } catch (Exception exception) {
            status.set("update check failed: " + exception.getMessage());
            HekuosGuard.LOGGER.warn("Update check failed", exception);
        }
    }

    private static void verifyJar(Path path, String expectedVersion) throws IOException {
        try (ZipFile jar = new ZipFile(path.toFile())) {
            var entry = jar.getEntry("fabric.mod.json");
            if (entry == null) throw new IOException("download is not a Fabric mod JAR");
            try (var reader = new java.io.InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
                if (!HekuosGuard.MOD_ID.equals(metadata.get("id").getAsString()) || !expectedVersion.equals(metadata.get("version").getAsString())) {
                    throw new IOException("downloaded JAR metadata does not match the release");
                }
            }
        }
    }
    private static int compareVersions(String left, String right) {
        String[] a = left.split("[.+-]");
        String[] b = right.split("[.+-]");
        int length = Math.max(a.length, b.length);
        for (int index = 0; index < length; index++) {
            String first = index < a.length ? a[index] : "0";
            String second = index < b.length ? b[index] : "0";
            try { int comparison = Integer.compare(Integer.parseInt(first), Integer.parseInt(second)); if (comparison != 0) return comparison; }
            catch (NumberFormatException ignored) { int comparison = first.compareTo(second); if (comparison != 0) return comparison; }
        }
        return 0;
    }
}
