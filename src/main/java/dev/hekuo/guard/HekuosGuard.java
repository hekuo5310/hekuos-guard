package dev.hekuo.guard;

import dev.hekuo.guard.command.GuardCommands;
import dev.hekuo.guard.config.GuardConfig;
import dev.hekuo.guard.core.ViolationManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-only entry point for hekuo's guard. */
public final class HekuosGuard implements ModInitializer {
    public static final String MOD_ID = "hekuos_guard";
    public static final Logger LOGGER = LoggerFactory.getLogger("hekuo's guard");
    private static final GuardConfig CONFIG = new GuardConfig();
    private static final ViolationManager VIOLATIONS = new ViolationManager(CONFIG);

    public static GuardConfig config() { return CONFIG; }
    public static ViolationManager violations() { return VIOLATIONS; }

    @Override
    public void onInitialize() {
        CONFIG.loadOrCreate();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> VIOLATIONS.start(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> VIOLATIONS.stop());
        ServerTickEvents.END_SERVER_TICK.register(VIOLATIONS::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> VIOLATIONS.join(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VIOLATIONS.leave(handler.player));
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (world.isClient || !(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            return VIOLATIONS.checkAttack(serverPlayer, target);
        });
        CommandRegistrationCallback.EVENT.register(GuardCommands::register);
        LOGGER.info("hekuo's guard initialized (server-only; technical automation is intentionally ignored)");
    }
}
