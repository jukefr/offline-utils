package fr.juke.mixin;

import fr.juke.auth.AuthManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks gameplay packets from players who have not completed /login. The
 * auth gate is intentionally conservative: movement, interactions and chat
 * are dropped until the player authenticates. /login and /register stay
 * available so a player can get through the gate.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockMoveVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockPlayerCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handleSpectatorAction", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockSpectator(ServerboundSpectatorActionPacket packet, CallbackInfo ci) {
        block(ci);
    }

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockChat(ServerboundChatPacket packet, CallbackInfo ci) {
        if (AuthManager.isPending(this.player)) {
            this.player.sendSystemMessage(AuthManager.msg(
                    "Use /login or /register before chatting.", ChatFormatting.RED));
            ci.cancel();
        }
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        if (AuthManager.isPending(this.player) && !allowedWhilePending(packet.command())) {
            this.player.sendSystemMessage(AuthManager.msg(
                    "Use /login or /register before running commands.", ChatFormatting.RED));
            ci.cancel();
        }
    }

    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void offlineUtils$blockSignedCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
        if (AuthManager.isPending(this.player) && !allowedWhilePending(packet.command())) {
            this.player.sendSystemMessage(AuthManager.msg(
                    "Use /login or /register before running commands.", ChatFormatting.RED));
            ci.cancel();
        }
    }

    private void block(CallbackInfo ci) {
        if (AuthManager.isPending(this.player)) {
            ci.cancel();
        }
    }

    private static boolean allowedWhilePending(String command) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        return normalized.equals("login") || normalized.startsWith("login ")
                || normalized.equals("register") || normalized.startsWith("register ");
    }
}
