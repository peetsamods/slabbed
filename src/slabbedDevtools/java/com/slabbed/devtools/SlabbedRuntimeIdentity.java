package com.slabbed.devtools;

import com.slabbed.Slabbed;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;

/** Captures share-safe runtime identity for the schema-6 manifest and startup line. */
public final class SlabbedRuntimeIdentity {
    private SlabbedRuntimeIdentity() {
    }

    public static LinkedHashMap<String, String> capture(String worldIdentity) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("minecraftVersion", versionOf("minecraft"));
        fields.put("forgeVersion", versionOf("forge"));
        fields.put("slabbedVersion", versionOf(Slabbed.MOD_ID));
        fields.put("devtoolsVersion", versionOf(SlabbedDevTools.MOD_ID));
        addArtifact(fields, "core", Slabbed.MOD_ID);
        addArtifact(fields, "addon", SlabbedDevTools.MOD_ID);
        Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        fields.put("profile", gameDir.getFileName() == null
                ? "unknown" : gameDir.getFileName().toString());
        fields.put("world", worldIdentity == null || worldIdentity.isBlank()
                ? "unknown" : worldIdentity);
        return fields;
    }

    private static String versionOf(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private static void addArtifact(
            LinkedHashMap<String, String> fields,
            String prefix,
            String modId) {
        try {
            IModFileInfo info = ModList.get().getModFileById(modId);
            if (info == null || info.getFile() == null) {
                fields.put(prefix + "File", "unknown");
                fields.put(prefix + "Sha256", "unknown");
                return;
            }
            Path path = info.getFile().getFilePath().toAbsolutePath().normalize();
            fields.put(prefix + "File", path.getFileName() == null
                    ? "unknown" : path.getFileName().toString());
            fields.put(prefix + "Sha256", Files.isRegularFile(path)
                    ? sha256(path) : "DEV_CLASSPATH");
        } catch (RuntimeException | IOException error) {
            fields.put(prefix + "File", "unavailable");
            fields.put(prefix + "Sha256", "unavailable");
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM lacks SHA-256", impossible);
        }
    }
}
