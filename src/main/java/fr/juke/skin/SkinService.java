package fr.juke.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import fr.juke.config.OfflineUtilsConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the premium (Mojang) skin for a username and copies the signed
 * textures property onto the offline-mode GameProfile.
 *
 * <p>Offline servers never talk to Mojang, so the profile they create has no
 * textures. The skin is looked up by username, which means it is cosmetic
 * only: it must never be treated as proof of account ownership.</p>
 */
public final class SkinService {
    private static final Logger LOGGER = LogManager.getLogger("offline-utils");
    private static final String USER_LOOKUP = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, CachedSkin> CACHE = new ConcurrentHashMap<>();

    private SkinService() {
    }

    public static boolean isEnabled() {
        return OfflineUtilsConfig.INSTANCE.skins.enabled;
    }

    /**
     * Applies the premium skin to a profile when one can be found. Runs on the
     * login network thread, so a timeout keeps a slow Mojang API from holding
     * up the connection for long.
     */
    public static GameProfile apply(GameProfile profile) {
        if (!isEnabled() || profile == null || profile.getName() == null) {
            return profile;
        }

        String name = profile.getName();
        CachedSkin skin = lookup(name);
        if (skin == null) {
            return profile;
        }

        // Some Minecraft versions hand us an immutable profile, so build a
        // copy and leave the original untouched.
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", new Property("textures", skin.value, skin.signature));
        return profile;
    }

    private static CachedSkin lookup(String username) {
        String key = username.toLowerCase();
        CachedSkin cached = CACHE.get(key);
        long ttl = OfflineUtilsConfig.INSTANCE.skins.cacheMinutes * 60_000L;
        if (cached != null && Instant.now().toEpochMilli() - cached.fetchedAt < ttl) {
            return cached;
        }

        try {
            Optional<String> id = fetchProfileId(username);
            if (id.isEmpty()) {
                return null;
            }

            Optional<CachedSkin> skin = fetchTextures(id.get());
            if (skin.isEmpty()) {
                return null;
            }

            CACHE.put(key, skin.get());
            return skin.get();
        } catch (Exception e) {
            LOGGER.warn("Could not look up premium skin for {}: {}", username, e.toString());
            if (cached != null) {
                return cached;
            }
            return null;
        }
    }

    private static Optional<String> fetchProfileId(String username) throws Exception {
        URI uri = URI.create(USER_LOOKUP + URLEncoder.encode(username, StandardCharsets.UTF_8));
        Optional<JsonObject> json = getJson(uri);
        if (json.isEmpty()) {
            return Optional.empty();
        }
        JsonElement id = json.get().get("id");
        if (id == null || id.isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(id.getAsString());
    }

    private static Optional<CachedSkin> fetchTextures(String id) throws Exception {
        URI uri = URI.create(SESSION_PROFILE + id + "?unsigned=false");
        Optional<JsonObject> json = getJson(uri);
        if (json.isEmpty()) {
            return Optional.empty();
        }

        JsonArray properties = json.get().getAsJsonArray("properties");
        if (properties == null) {
            return Optional.empty();
        }

        for (JsonElement element : properties) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            String value = property.get("value").getAsString();
            String signature = property.has("signature") ? property.get("signature").getAsString() : null;
            if (value == null || value.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CachedSkin(value, signature, Instant.now().toEpochMilli()));
        }
        return Optional.empty();
    }

    private static Optional<JsonObject> getJson(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(OfflineUtilsConfig.INSTANCE.skins.timeoutMillis))
                .header("Accept", "application/json")
                .header("User-Agent", "offline-utils/1.0 (https://github.com/jukefr/offline-utils)")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Mojang API returned HTTP " + response.statusCode());
        }
        return Optional.of(JsonParser.parseString(response.body()).getAsJsonObject());
    }

    private record CachedSkin(String value, String signature, long fetchedAt) {
    }
}
