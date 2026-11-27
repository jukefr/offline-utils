package fr.juke.auth;

import fr.juke.config.OfflineUtilsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps unauthenticated players frozen until they run {@code /login} or
 * {@code /register}. This is a server-side password gate, not a replacement
 * for online-mode authentication.
 */
public final class AuthManager {
    private static final Logger LOGGER = LogManager.getLogger("offline-utils");
    private static final PasswordStore STORE = new PasswordStore();
    private static final Map<UUID, PendingLogin> PENDING = new ConcurrentHashMap<>();
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private AuthManager() {
    }

    public static PasswordStore store() {
        return STORE;
    }

    public static boolean isPending(ServerPlayer player) {
        return player != null && PENDING.containsKey(player.getUUID());
    }

    public static void onJoin(ServerPlayer player) {
        if (!OfflineUtilsConfig.INSTANCE.auth.enabled) {
            return;
        }

        String name = player.getGameProfile().name();
        if (isSessionValid(name, player.getIpAddress())) {
            player.sendSystemMessage(msg("Welcome back, " + name + ".", ChatFormatting.GREEN));
            return;
        }

        long timeout = OfflineUtilsConfig.INSTANCE.auth.loginTimeoutSeconds;
        PENDING.put(player.getUUID(), new PendingLogin(name, Instant.now().getEpochSecond() + timeout));
        player.setInvulnerable(true);

        player.sendSystemMessage(msg("This server uses /login authentication.", ChatFormatting.GOLD));
        if (STORE.has(name)) {
            player.sendSystemMessage(msg("Run /login <password> to continue.", ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(msg("Run /register <password> <password> to create your password.", ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(msg("You have " + timeout + " seconds before you are disconnected.", ChatFormatting.GRAY));
    }

    public static void onDisconnect(ServerPlayer player) {
        if (player != null) {
            PENDING.remove(player.getUUID());
        }
    }

    public static void tick(MinecraftServer server) {
        if (!OfflineUtilsConfig.INSTANCE.auth.enabled || PENDING.isEmpty()) {
            return;
        }

        long now = Instant.now().getEpochSecond();
        for (Map.Entry<UUID, PendingLogin> entry : PENDING.entrySet()) {
            if (entry.getValue().deadline > now) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            PENDING.remove(entry.getKey());
            if (player != null) {
                player.connection.disconnect(msg("Login timed out. Reconnect and use /login.", ChatFormatting.RED));
            }
        }
    }

    public static void register(ServerPlayer player, String password, String confirm) {
        PendingLogin pending = pending(player);
        if (pending == null) {
            player.sendSystemMessage(msg("You are already logged in.", ChatFormatting.GRAY));
            return;
        }
        if (STORE.has(pending.username)) {
            player.sendSystemMessage(msg("This account already has a password. Use /login <password>.", ChatFormatting.RED));
            return;
        }
        if (!validLength(password)) {
            player.sendSystemMessage(msg("Password must be between "
                    + OfflineUtilsConfig.INSTANCE.auth.minPasswordLength + " and "
                    + OfflineUtilsConfig.INSTANCE.auth.maxPasswordLength + " characters.", ChatFormatting.RED));
            return;
        }
        if (!password.equals(confirm)) {
            player.sendSystemMessage(msg("Passwords do not match.", ChatFormatting.RED));
            return;
        }

        STORE.put(pending.username, player.getUUID().toString(), password);
        complete(player, pending);
        player.sendSystemMessage(msg("Password created. Welcome to the server!", ChatFormatting.GREEN));
        LOGGER.info("Registered password for {}", pending.username);
    }

    public static void login(ServerPlayer player, String password) {
        PendingLogin pending = pending(player);
        if (pending == null) {
            player.sendSystemMessage(msg("You are already logged in.", ChatFormatting.GRAY));
            return;
        }
        if (!STORE.has(pending.username)) {
            player.sendSystemMessage(msg("No password is set. Use /register <password> <password>.", ChatFormatting.RED));
            return;
        }

        if (STORE.verify(pending.username, password)) {
            complete(player, pending);
            player.sendSystemMessage(msg("Logged in. Welcome back!", ChatFormatting.GREEN));
            return;
        }

        pending.attempts++;
        int left = OfflineUtilsConfig.INSTANCE.auth.maxAttempts - pending.attempts;
        if (left <= 0) {
            PENDING.remove(player.getUUID());
            player.connection.disconnect(msg("Too many failed login attempts.", ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(msg("Wrong password. " + left + " attempt" + (left == 1 ? "" : "s") + " left.", ChatFormatting.RED));
    }

    private static void complete(ServerPlayer player, PendingLogin pending) {
        PENDING.remove(player.getUUID());
        player.setInvulnerable(false);

        int minutes = OfflineUtilsConfig.INSTANCE.auth.sessionMinutes;
        if (minutes > 0) {
            SESSIONS.put(pending.username.toLowerCase(),
                    new Session(player.getIpAddress(), Instant.now().getEpochSecond() + minutes * 60L));
        }
    }

    private static boolean isSessionValid(String username, String ip) {
        Session session = SESSIONS.get(username.toLowerCase());
        if (session == null) {
            return false;
        }
        if (session.expiresAt <= Instant.now().getEpochSecond()) {
            SESSIONS.remove(username.toLowerCase());
            return false;
        }
        return session.ip.equals(ip);
    }

    private static PendingLogin pending(ServerPlayer player) {
        if (!OfflineUtilsConfig.INSTANCE.auth.enabled) {
            return null;
        }
        return PENDING.get(player.getUUID());
    }

    private static boolean validLength(String password) {
        return password != null
                && password.length() >= OfflineUtilsConfig.INSTANCE.auth.minPasswordLength
                && password.length() <= OfflineUtilsConfig.INSTANCE.auth.maxPasswordLength;
    }

    public static Component msg(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    private static final class PendingLogin {
        private final String username;
        private final long deadline;
        private int attempts;

        private PendingLogin(String username, long deadline) {
            this.username = username;
            this.deadline = deadline;
        }
    }

    private record Session(String ip, long expiresAt) {
    }
}
