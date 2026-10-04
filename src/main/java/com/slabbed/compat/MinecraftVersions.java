package com.slabbed.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * The running Minecraft version, for the few seams where one jar serves more than one game version.
 *
 * <p>Minecraft 26.x ships under its real names and Fabric does not remap mods there, so a renamed
 * class or member is a hard link failure on the version that lacks it. Every such seam is isolated
 * behind a question answered here once, at class initialisation, from the loader's own version
 * record; nothing reads a version string at call time.
 */
public final class MinecraftVersions {

    /** True on Minecraft 26.3 and newer; false on 26.2. */
    public static final boolean AT_LEAST_26_3 = atLeast("26.3");

    private MinecraftVersions() {
    }

    static boolean atLeast(String version) {
        Version running = FabricLoader.getInstance().getModContainer("minecraft")
                .orElseThrow(() -> new IllegalStateException("no minecraft mod container"))
                .getMetadata().getVersion();
        try {
            return VersionPredicate.parse(">=" + version).test(running);
        } catch (VersionParsingException e) {
            throw new IllegalStateException("bad version bound " + version, e);
        }
    }
}
