package fr.juke.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import fr.juke.util.Paths;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * The single configuration file shared by the Fabric and NeoForge builds.
 * Unknown fields written by newer versions are ignored; missing fields fall
 * back to the defaults defined here.
 */
public final class OfflineUtilsConfig {
    private static final Logger LOGGER = LogManager.getLogger("offline-utils");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "offline-utils.json";

    public static final OfflineUtilsConfig INSTANCE = new OfflineUtilsConfig();

    public int configVersion = 1;
    public Auth auth = new Auth();
    public Skins skins = new Skins();

    public static final class Auth {
        public boolean enabled = true;
        public int minPasswordLength = 6;
        public int maxPasswordLength = 128;
        public int loginTimeoutSeconds = 120;
        public int maxAttempts = 5;
        /** 0 disables session reuse. Otherwise a player is remembered for this many minutes on the same IP. */
        public int sessionMinutes = 0;
    }

    public static final class Skins {
        public boolean enabled = true;
        public int timeoutMillis = 3000;
        public int cacheMinutes = 360;
    }

    public void load() {
        Path config = path();
        try {
            if (Files.isDirectory(config)) {
                Files.delete(config);
            }

            if (Files.notExists(config)) {
                sanitize();
                saveQuietly();
                return;
            }

            OfflineUtilsConfig read;
            try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                read = GSON.fromJson(reader, OfflineUtilsConfig.class);
            }
            if (read == null) {
                throw new IOException("Configuration file is empty");
            }

            this.configVersion = read.configVersion;
            this.auth = read.auth == null ? new Auth() : read.auth;
            this.skins = read.skins == null ? new Skins() : read.skins;
            sanitize();
            saveQuietly();
        } catch (Exception e) {
            LOGGER.error("Could not load {}, using defaults", config, e);
            backup(config);
            this.configVersion = 1;
            this.auth = new Auth();
            this.skins = new Skins();
            sanitize();
            saveQuietly();
        }
    }

    private void sanitize() {
        if (this.configVersion < 1) {
            this.configVersion = 1;
        }
        if (this.auth == null) {
            this.auth = new Auth();
        }
        if (this.skins == null) {
            this.skins = new Skins();
        }
        this.auth.minPasswordLength = clamp(this.auth.minPasswordLength, 1, 128);
        this.auth.maxPasswordLength = clamp(this.auth.maxPasswordLength, this.auth.minPasswordLength, 256);
        this.auth.loginTimeoutSeconds = clamp(this.auth.loginTimeoutSeconds, 10, 3600);
        this.auth.maxAttempts = clamp(this.auth.maxAttempts, 1, 100);
        this.auth.sessionMinutes = clamp(this.auth.sessionMinutes, 0, 10080);
        this.skins.timeoutMillis = clamp(this.skins.timeoutMillis, 250, 15000);
        this.skins.cacheMinutes = clamp(this.skins.cacheMinutes, 1, 10080);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public void save() throws IOException {
        Path config = path();
        Files.createDirectories(config.getParent());

        Path temp = config.resolveSibling(FILE_NAME + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }

        try {
            Files.move(temp, config, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, config, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void saveQuietly() {
        try {
            save();
        } catch (IOException e) {
            LOGGER.error("Could not save configuration file", e);
        }
    }

    private static Path path() {
        return Paths.configDir().resolve(FILE_NAME);
    }

    private static void backup(Path config) {
        try {
            Path backup = config.resolveSibling(FILE_NAME + ".broken-" + Instant.now().toEpochMilli());
            Files.move(config, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Moved unreadable configuration file to {}", backup);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable configuration file", e);
        }
    }
}
