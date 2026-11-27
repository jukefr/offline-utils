package fr.juke.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Loader-specific paths live here so the rest of the code stays shared between
 * the Fabric and NeoForge builds.
 */
public final class Paths {
    private Paths() {
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDirectory().toPath();
    }
}
