package dev.hekuo.guard.core;

import dev.hekuo.guard.HekuosGuard;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

final class AuditLogger {
    void write(ServerPlayerEntity player, CheckType type, String detail, int score, long tick) {
        try {
            Path directory = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("hekuos_guard");
            Files.createDirectories(directory);
            Vec3d pos = player.getPos();
            String line = "{\"tick\":" + tick + ",\"player\":\"" + escape(player.getName().getString())
                    + "\",\"uuid\":\"" + player.getUuidAsString() + "\",\"check\":\"" + type
                    + "\",\"score\":" + score + ",\"x\":" + pos.x + ",\"y\":" + pos.y + ",\"z\":" + pos.z
                    + ",\"detail\":\"" + escape(detail) + "\"}" + System.lineSeparator();
            Files.writeString(directory.resolve(LocalDate.now() + ".jsonl"), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            HekuosGuard.LOGGER.warn("Unable to write anti-cheat audit record", exception);
        }
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
