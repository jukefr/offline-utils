package fr.juke;

import fr.juke.auth.AuthManager;
import fr.juke.command.AuthCommands;
import fr.juke.config.OfflineUtilsConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(OfflineUtils.MOD_ID)
public class OfflineUtils {
    public static final String MOD_ID = "offline_utils";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static int tickCounter;

    public OfflineUtils(IEventBus modEventBus, ModContainer modContainer) {
        OfflineUtilsConfig.INSTANCE.load();

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);

        LOGGER.info("Loaded offline-utils (auth: {}, skins: {})",
                OfflineUtilsConfig.INSTANCE.auth.enabled,
                OfflineUtilsConfig.INSTANCE.skins.enabled);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        AuthCommands.register(event.getDispatcher());
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AuthManager.onJoin(player);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AuthManager.onDisconnect(player);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        AuthManager.tick(event.getServer());
    }
}
