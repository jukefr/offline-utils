package fr.juke.mixin;

import com.mojang.authlib.GameProfile;
import fr.juke.skin.SkinService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Restores the premium skin before the player is added to the world. The
 * login listener runs on its own network thread, so a Mojang lookup here does
 * not stall the server tick loop.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @ModifyVariable(method = "finishLoginAndWaitForClient", at = @At("HEAD"), argsOnly = true)
    private GameProfile offlineUtils$restorePremiumSkin(GameProfile profile) {
        if (profile != null && !this.server.usesAuthentication()) {
            return SkinService.apply(profile);
        }
        return profile;
    }
}
