package dev.hekuo.guard.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.hekuo.guard.HekuosGuard;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;

public final class GuardCommands {
    private GuardCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment environment) {
        var root = CommandManager.literal("hekuosguard").requires(source -> source.hasPermissionLevel(3))
                .then(CommandManager.literal("reload").executes(GuardCommands::reload))
                .then(CommandManager.literal("alerts").executes(GuardCommands::alerts))
                .then(CommandManager.literal("status").then(CommandManager.argument("player", EntityArgumentType.player()).executes(GuardCommands::status)))
                .then(CommandManager.literal("reset").then(CommandManager.argument("player", EntityArgumentType.player()).executes(GuardCommands::reset)))
                .then(CommandManager.literal("exempt")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1)).executes(GuardCommands::exempt))))
                .then(unbanCommand());
        dispatcher.register(root);
        dispatcher.register(CommandManager.literal("hg").requires(source -> source.hasPermissionLevel(3))
                .then(CommandManager.literal("reload").executes(GuardCommands::reload))
                .then(CommandManager.literal("alerts").executes(GuardCommands::alerts))
                .then(CommandManager.literal("status").then(CommandManager.argument("player", EntityArgumentType.player()).executes(GuardCommands::status)))
                .then(CommandManager.literal("reset").then(CommandManager.argument("player", EntityArgumentType.player()).executes(GuardCommands::reset)))
                .then(CommandManager.literal("exempt").then(CommandManager.argument("player", EntityArgumentType.player()).then(CommandManager.argument("seconds", IntegerArgumentType.integer(1)).executes(GuardCommands::exempt))))
                .then(unbanCommand()));
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        try {
            HekuosGuard.config().reload();
            HekuosGuard.violations().sendClientRulesToAll();
            context.getSource().sendFeedback(() -> Text.literal("[HG] configuration reloaded"), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendError(Text.literal("[HG] configuration rejected: " + exception.getMessage()));
            return 0;
        }
    }
    private static int alerts(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player;
        try { player = context.getSource().getPlayerOrThrow(); }
        catch (Exception exception) { context.getSource().sendError(Text.literal("[HG] this command needs a player")); return 0; }
        boolean enabled = HekuosGuard.violations().toggleAlerts(player);
        context.getSource().sendFeedback(() -> Text.literal("[HG] alerts " + (enabled ? "enabled" : "disabled")), false);
        return 1;
    }
    private static int status(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
        context.getSource().sendFeedback(() -> Text.literal("[HG] " + player.getName().getString() + " score=" + HekuosGuard.violations().score(player)), false);
        return 1;
    }
    private static int reset(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
        HekuosGuard.violations().reset(player);
        context.getSource().sendFeedback(() -> Text.literal("[HG] reset " + player.getName().getString()), true);
        return 1;
    }
    private static int exempt(CommandContext<ServerCommandSource> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        HekuosGuard.violations().exempt(player, seconds);
        context.getSource().sendFeedback(() -> Text.literal("[HG] exempted " + player.getName().getString() + " for " + seconds + " seconds"), true);
        return 1;
    }
    private static int unban(CommandContext<ServerCommandSource> context) {
        String playerName = StringArgumentType.getString(context, "player");
        boolean removed = HekuosGuard.violations().unbanByPlayerName(playerName);
        context.getSource().sendFeedback(() -> Text.literal("[HG] " + (removed ? "unbanned " : "no active ban for ") + playerName), true);
        return removed ? 1 : 0;
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> unbanCommand() {
        return CommandManager.literal("unban")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(GuardCommands::unban)
                        .then(CommandManager.literal("--force").executes(GuardCommands::forceUnban)));
    }
    private static int forceUnban(CommandContext<ServerCommandSource> context) {
        String playerName = StringArgumentType.getString(context, "player");
        boolean removed = HekuosGuard.violations().forceUnbanByPlayerName(playerName);
        context.getSource().sendFeedback(() -> Text.literal("[HG] " + (removed ? "force-unbanned and cleared history for " : "no ban history for ") + playerName), true);
        return removed ? 1 : 0;
    }
}
