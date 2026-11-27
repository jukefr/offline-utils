package fr.juke.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import fr.juke.util.Paths;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stores PBKDF2 password hashes in {@code config/offline-utils-auth.json}.
 * Plain-text passwords are never written to disk.
 */
public final class PasswordStore {
    private static final Logger LOGGER = LogManager.getLogger("offline-utils");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "offline-utils-auth.json";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final Type USER_MAP = new TypeToken<LinkedHashMap<String, StoredUser>>() { }.getType();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, StoredUser> users = new LinkedHashMap<>();
    private boolean dirty;

    public PasswordStore() {
        load();
    }

    public boolean has(String username) {
        return users.containsKey(key(username));
    }

    public synchronized void put(String username, String uuid, String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        StoredUser user = new StoredUser();
        user.username = username;
        user.uuid = uuid;
        user.salt = Base64.getEncoder().encodeToString(salt);
        user.hash = Base64.getEncoder().encodeToString(hash(password, salt));
        user.iterations = ITERATIONS;
        user.updatedAt = System.currentTimeMillis();
        users.put(key(username), user);
        dirty = true;
        save();
    }

    public synchronized boolean verify(String username, String password) {
        StoredUser user = users.get(key(username));
        if (user == null) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(user.salt);
        byte[] expected = Base64.getDecoder().decode(user.hash);
        byte[] actual = hash(password, salt, user.iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    public synchronized Optional<StoredUser> get(String username) {
        return Optional.ofNullable(users.get(key(username)));
    }

    public synchronized boolean remove(String username) {
        boolean removed = users.remove(key(username)) != null;
        if (removed) {
            dirty = true;
            save();
        }
        return removed;
    }

    public synchronized int size() {
        return users.size();
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase();
    }

    private static byte[] hash(String password, byte[] salt) {
        return hash(password, salt, ITERATIONS);
    }

    private static byte[] hash(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec)
                        .getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 is unavailable", e);
        }
    }

    private void load() {
        Path path = path();
        if (Files.notExists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, StoredUser> read = GSON.fromJson(reader, USER_MAP);
            if (read != null) {
                users.putAll(read);
            }
        } catch (Exception e) {
            LOGGER.error("Could not read {}", path, e);
        }
    }

    private void save() {
        if (!dirty) {
            return;
        }
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(FILE_NAME + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(users, USER_MAP, writer);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException e) {
            LOGGER.error("Could not save {}", path, e);
        }
    }

    private static Path path() {
        return Paths.configDir().resolve(FILE_NAME);
    }

    public static final class StoredUser {
        public String username;
        public String uuid;
        public String salt;
        public String hash;
        public int iterations = ITERATIONS;
        public long updatedAt;
    }
}
