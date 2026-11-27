package fr.juke.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.juke.auth.AuthManager;
import fr.juke.config.OfflineUtilsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import static fr.juke.auth.AuthManager.msg;

public final class AuthCommands {
    private AuthCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            AuthManager.login(player, StringArgumentType.getString(ctx, "password"));
                            return 1;
                        })));

        dispatcher.register(Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.word())
                        .then(Commands.argument("confirm", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    AuthManager.register(player,
                                            StringArgumentType.getString(ctx, "password"),
                                            StringArgumentType.getString(ctx, "confirm"));
                                    return 1;
                                }))));

        dispatcher.register(Commands.literal("offlineutils")
                .then(Commands.literal("reload")
                        .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> {
                            OfflineUtilsConfig.INSTANCE.load();
                            ctx.getSource().sendSystemMessage(msg("offline-utils config reloaded.", ChatFormatting.GREEN));
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> {
                            OfflineUtilsConfig cfg = OfflineUtilsConfig.INSTANCE;
                            ctx.getSource().sendSystemMessage(msg("offline-utils 1.0.0", ChatFormatting.AQUA));
                            ctx.getSource().sendSystemMessage(msg("auth: " + cfg.auth.enabled
                                    + ", skins: " + cfg.skins.enabled
                                    + ", accounts: " + AuthManager.store().size(), ChatFormatting.GRAY));
                            return 1;
                        }))
                .then(Commands.literal("reset")
                        .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "player");
                                    boolean removed = AuthManager.store().remove(name);
                                    ctx.getSource().sendSystemMessage(msg(removed
                                            ? "Removed the password for " + name + "."
                                            : "No password found for " + name + ".",
                                            removed ? ChatFormatting.GREEN : ChatFormatting.RED));
                                    return removed ? 1 : 0;
                                }))));
    }
}
