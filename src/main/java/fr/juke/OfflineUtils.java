package fr.juke;

import fr.juke.auth.AuthManager;
import fr.juke.command.AuthCommands;
import fr.juke.config.OfflineUtilsConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OfflineUtils implements ModInitializer {
    public static final String MOD_ID = "offline-utils";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static int tickCounter;

    @Override
    public void onInitialize() {
        OfflineUtilsConfig.INSTANCE.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AuthCommands.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                AuthManager.onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                AuthManager.onDisconnect(handler.player));

        ServerTickEvents.END_SERVER_TICK.register(OfflineUtils::onTick);

        LOGGER.info("Loaded offline-utils (auth: {}, skins: {})",
                OfflineUtilsConfig.INSTANCE.auth.enabled,
                OfflineUtilsConfig.INSTANCE.skins.enabled);
    }

    private static void onTick(MinecraftServer server) {
        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        AuthManager.tick(server);
    }
}
