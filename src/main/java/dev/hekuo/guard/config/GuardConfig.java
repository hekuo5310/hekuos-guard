package dev.hekuo.guard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.hekuo.guard.HekuosGuard;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Mutable, atomically replaced JSON configuration. */
public final class GuardConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("hekuos_guard.json");
    private volatile Values values = new Values();

    public Values get() { return values; }

    public synchronized void loadOrCreate() {
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.writeString(path, GSON.toJson(new Values()), StandardCharsets.UTF_8);
                values = new Values();
                return;
            }
            reload();
        } catch (IOException exception) {
            HekuosGuard.LOGGER.error("Unable to create guard configuration", exception);
        }
    }

    public synchronized void reload() throws IOException {
        Values candidate = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Values.class);
        if (candidate == null) throw new IOException("configuration is empty");
        candidate.validate();
        values = candidate;
        HekuosGuard.LOGGER.info("Reloaded {}", path);
    }

    public static final class Values {
        public boolean enabled = true;
        public Movement movement = new Movement();
        public Combat combat = new Combat();
        public Packets packets = new Packets();
        public Enforcement enforcement = new Enforcement();
        public Logging logging = new Logging();
        public ClientDetection clientDetection = new ClientDetection();

        void validate() throws IOException {
            if (movement == null || combat == null || packets == null || enforcement == null || logging == null || clientDetection == null || clientDetection.secureHandshake == null) throw new IOException("missing configuration section");
            if (movement.maxHorizontalPerTick <= 0 || movement.maxCumulativeHorizontalPerTick <= 0 || movement.maxVerticalPerTick <= 0 || movement.maxCoordinate <= 0 || movement.joinGraceSeconds < 0 || movement.flightAirTicks < 20 || movement.waterWalkTicks < 20) throw new IOException("movement limits must be positive");
            if (combat.reachTolerance < 0 || combat.invulnerabilityTicks < 20 || packets.movePacketsPerSecond < 1 || packets.attackPacketsPerSecond < 1 || packets.interactPacketsPerSecond < 1) throw new IOException("packet limits and reach tolerance are invalid");
            if (enforcement.alertAt < 1 || enforcement.kickAt <= enforcement.alertAt || enforcement.decaySeconds < 1) throw new IOException("enforcement thresholds are invalid");
            if (enforcement.banBaseSeconds < 1 || enforcement.fastScoreWindowSeconds < 1 || enforcement.fastScoreThreshold < 1 || enforcement.permanentBanLevel < 2) throw new IOException("ban settings are invalid");
            if (clientDetection.secureHandshake.timeoutSeconds < 1 || clientDetection.secureHandshake.timeoutSeconds > 120) throw new IOException("secure handshake timeout must be between 1 and 120 seconds");
            for (String hash : clientDetection.secureHandshake.allowedClientSha256) {
                if (hash == null || !hash.toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}")) throw new IOException("allowedClientSha256 must contain SHA-256 hex values");
            }
        }
    }

    public static final class Movement {
        public boolean enabled = true;
        public double maxHorizontalPerTick = 0.85;
        /** Total horizontal displacement accepted in a single server tick (Timer check). */
        public double maxCumulativeHorizontalPerTick = 1.2;
        public double maxVerticalPerTick = 1.25;
        public double maxCoordinate = 29_999_984.0;
        public int safeTicks = 5;
        public int transitionGraceTicks = 20;
        /** No movement scoring immediately after join; vanilla still settles position and chunks. */
        public int joinGraceSeconds = 15;
        /** Continuous unsupported airtime before a survival player is treated as flying. */
        public int flightAirTicks = 100;
        /** Continuous unsupported water-surface standing before it is treated as water walk. */
        public int waterWalkTicks = 40;
    }

    public static final class Combat {
        public boolean enabled = true;
        public double reachTolerance = 0.25;
        public int maxTargetsPerSecond = 8;
        /** Continuous survival-mode invulnerability before it is considered illicit. */
        public int invulnerabilityTicks = 40;
    }

    public static final class Packets {
        public boolean enabled = true;
        public int movePacketsPerSecond = 100;
        public int attackPacketsPerSecond = 40;
        public int interactPacketsPerSecond = 80;
        public int burstMultiplier = 2;
    }

    public static final class Enforcement {
        public int alertAt = 5;
        public int kickAt = 20;
        public int decaySeconds = 15;
        public int temporaryExemptionMaxSeconds = 3600;
        /** The first automated ban length. Each ban level doubles it. */
        public long banBaseSeconds = 3600;
        /** Score added within this window determines whether escalation skips levels. */
        public int fastScoreWindowSeconds = 60;
        public int fastScoreThreshold = 20;
        /** Level five is permanent by default: 1h, 2h, 4h, 8h, permanent. */
        public int permanentBanLevel = 5;
    }

    public static final class Logging {
        public boolean jsonlAudit = true;
        public boolean staffAlerts = true;
    }

    /** Rules are sent only to clients that have hekuo's guard installed. */
    public static final class ClientDetection {
        public boolean enabled = true;
        /**
         * Reject clients that do not advertise support for the guard client payload.
         * Keep this off by default so vanilla clients can still join a normal server.
         */
        public boolean requireClientMod = false;
        public SecureHandshake secureHandshake = new SecureHandshake();
        /** Exact Fabric mod ids to permanently ban when reported by the optional client companion. */
        public List<String> blockedModIds = new ArrayList<>(List.of(
                "wurst", "meteor-client", "aristois", "bleachhack", "liquidbounce",
                "inertia", "impact", "kami", "lambda", "rusherhack", "coffee", "konas"));
    }

    /** Opt-in encrypted challenge/proof; it is not a replacement for server-side checks. */
    public static final class SecureHandshake {
        public boolean enabled = false;
        public int timeoutSeconds = 10;
        /** When enabled, only the listed SHA-256 fingerprints of this client JAR are accepted. */
        public boolean requireKnownIntegrity = false;
        public List<String> allowedClientSha256 = new ArrayList<>();
    }
}
