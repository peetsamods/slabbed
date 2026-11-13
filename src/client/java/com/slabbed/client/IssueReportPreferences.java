package com.slabbed.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Local menu preference; never changes worlds, report contents or server settings. */
final class IssueReportPreferences {
    private static boolean showPauseButton = true;

    static void load() {
        showPauseButton = read(file());
    }

    static boolean showPauseButton() {
        return showPauseButton;
    }

    static boolean setShowPauseButton(boolean value) {
        if (!write(file(), value)) return false;
        showPauseButton = value;
        return true;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("slabbed-report-menu.json");
    }

    static boolean read(Path path) {
        try {
            JsonObject data = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var value = data.get("showPauseButton");
            return value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isBoolean() || value.getAsBoolean();
        } catch (IOException | RuntimeException failure) {
            return true;
        }
    }

    static boolean write(Path path, boolean value) {
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "slabbed-report-menu-", ".tmp");
            Files.writeString(temporary, "{\n  \"showPauseButton\": " + value + "\n}\n");
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private IssueReportPreferences() { }
}
