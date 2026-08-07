package com.slabbed.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persisted, client-side set of world/server keys where the player clicked "don't show this
 * again" on the beta notice. Deliberately scoped per-world (not a single global on/off switch)
 * so dismissing it in one world doesn't silence it in a brand new one — an explicit maintainer request.
 */
final class BetaNoticeDismissedWorlds {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("slabbed")
            .resolve("dismissed-beta-notice-worlds.json");

    private static Set<String> cache;

    private BetaNoticeDismissedWorlds() {
    }

    static synchronized boolean isDismissed(String worldKey) {
        if (worldKey == null) {
            return false;
        }
        load();
        return cache.contains(worldKey);
    }

    static synchronized void dismiss(String worldKey) {
        if (worldKey == null) {
            return;
        }
        load();
        if (cache.add(worldKey)) {
            save();
        }
    }

    private static void load() {
        if (cache != null) {
            return;
        }
        cache = new LinkedHashSet<>();
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            String json = Files.readString(FILE);
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : array) {
                cache.add(element.getAsString());
            }
        } catch (IOException | RuntimeException e) {
            // Corrupt or unreadable file — start fresh rather than crash the client.
            cache = new LinkedHashSet<>();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonArray array = new JsonArray();
            for (String key : cache) {
                array.add(key);
            }
            Files.writeString(FILE, new Gson().toJson(array));
        } catch (IOException e) {
            // Best-effort persistence; failing to write shouldn't crash the client, worst case
            // the notice reappears next time.
        }
    }
}
