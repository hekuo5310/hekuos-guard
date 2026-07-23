package dev.hekuo.guard.client;

import dev.hekuo.guard.HekuosGuard;
import dev.hekuo.guard.network.ClientModReportPayload;
import dev.hekuo.guard.network.ClientRulesPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
        HekuosGuard.LOGGER.info("hekuo's guard client companion initialized");
    }
}
