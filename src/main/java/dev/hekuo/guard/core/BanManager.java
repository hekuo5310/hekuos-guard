package dev.hekuo.guard.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.hekuo.guard.HekuosGuard;
import dev.hekuo.guard.config.GuardConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Own JSON ban store so bans survive restarts without requiring a permissions mod. */
final class BanManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("hekuos_guard_bans.json");
    private final Map<UUID, BanRecord> bans = new HashMap<>();

    synchronized void load() {
        try {
            if (!Files.exists(path)) return;
            Store store = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Store.class);
            if (store != null && store.bans != null) {
                bans.clear();
                for (BanRecord record : store.bans) bans.put(record.uuid, record);
                removeExpired();
            }
        } catch (IOException exception) {
            HekuosGuard.LOGGER.error("Unable to load hekuo's guard bans", exception);
        }
    }

    synchronized BanRecord ban(ServerPlayerEntity player, int speedLevel, GuardConfig.Enforcement settings) {
        removeExpired();
        BanRecord previous = bans.get(player.getUuid());
        int level = Math.max(1, (previous == null ? 0 : previous.level) + speedLevel);
        long expiresAt = level >= settings.permanentBanLevel ? 0L : Instant.now().plusSeconds(durationSeconds(level, settings)).toEpochMilli();
        BanRecord record = new BanRecord(player.getUuid(), player.getName().getString(), level, expiresAt, Instant.now().toEpochMilli());
        bans.put(player.getUuid(), record);
        save();
        return record;
    }

    synchronized BanRecord active(UUID uuid) {
        BanRecord record = bans.get(uuid);
        if (record != null && record.expiresAt != 0L && record.expiresAt <= Instant.now().toEpochMilli()) {
            bans.remove(uuid);
            save();
            return null;
        }
        return record;
    }

    synchronized boolean unban(UUID uuid) {
        boolean removed = bans.remove(uuid) != null;
        if (removed) save();
        return removed;
    }

    synchronized boolean unbanByPlayerName(String playerName) {
        UUID uuid = bans.values().stream()
                .filter(record -> record.player != null && record.player.equalsIgnoreCase(playerName))
                .map(record -> record.uuid)
                .findFirst()
                .orElse(null);
        return uuid != null && unban(uuid);
    }

    Text message(BanRecord record) { return Text.literal(messageText(record)); }

    String messageText(BanRecord record) {
        String duration = record.expiresAt == 0L ? "永久" : formatDuration(Math.max(1, record.expiresAt - Instant.now().toEpochMilli()));
        return "您已被封禁\n\n处罚时间：" + duration
                + "\n\n如果您认为这是一次误判，\n请前往QQ群 1002798978 联系群主申诉。"
                + "\n\n反作弊是一场长期的战斗，\n我们会努力保护每一位玩家的游戏体验。"
                + "\n同时，检测系统也可能存在不足，\n对于可能出现的误封，我们深表歉意。";
    }

    static long durationSeconds(int level, GuardConfig.Enforcement settings) {
        long multiplier = 1L << Math.min(30, Math.max(0, level - 1));
        return Math.multiplyExact(settings.banBaseSeconds, multiplier);
    }

    private void removeExpired() {
        if (bans.values().removeIf(record -> record.expiresAt != 0L && record.expiresAt <= Instant.now().toEpochMilli())) save();
    }
    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Store store = new Store();
            store.bans = bans.values().toArray(BanRecord[]::new);
            Files.writeString(path, GSON.toJson(store), StandardCharsets.UTF_8);
        } catch (IOException exception) { HekuosGuard.LOGGER.error("Unable to save hekuo's guard bans", exception); }
    }

    static final class Store { BanRecord[] bans = new BanRecord[0]; }
    static final class BanRecord {
        UUID uuid;
        String player;
        int level;
        long expiresAt; // 0 is permanent
        long bannedAt;
        BanRecord(UUID uuid, String player, int level, long expiresAt, long bannedAt) { this.uuid = uuid; this.player = player; this.level = level; this.expiresAt = expiresAt; this.bannedAt = bannedAt; }
    }
    private static String formatDuration(long millis) {
        long seconds = Duration.ofMillis(millis).toSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + "小时" + (minutes > 0 ? minutes + "分钟" : "") : Math.max(1, minutes) + "分钟";
    }
}
