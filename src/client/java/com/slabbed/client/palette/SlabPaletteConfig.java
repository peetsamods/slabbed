package com.slabbed.client.palette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted client-side settings for the {@link SlabPaletteScreen}: which edge the grid docks to,
 * its scale, how many rows are shown, and the 45 item ids that fill it. Mirrors the
 * {@code BetaNoticeDismissedWorlds} precedent — a plain JSON file under the Fabric config dir, with
 * a corrupt/missing file falling back to test-kit defaults and being rewritten.
 *
 * <p>Deliberately free of any client-render imports so its serialization round-trips headlessly in a
 * gametest.
 */
public final class SlabPaletteConfig {

    /** Which screen edge the palette docks to. */
    public enum DockSide {
        LEFT,
        RIGHT
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("slabbed-palette.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SlabPaletteConfig loaded;

    private DockSide side = DockSide.RIGHT;
    private float scale = 1.0f;
    private int rows = SlabTestKit.ROWS;
    private final List<String> itemIds = new ArrayList<>();

    private SlabPaletteConfig() {
        fillDefaultItems();
    }

    /** The lazily-loaded singleton, read from disk on first use (defaults if absent/corrupt). */
    public static synchronized SlabPaletteConfig get() {
        if (loaded == null) {
            loaded = read();
        }
        return loaded;
    }

    private void fillDefaultItems() {
        itemIds.clear();
        for (Identifier id : SlabTestKit.PALETTE) {
            itemIds.add(id.toString());
        }
    }

    // --- accessors -----------------------------------------------------------------------------

    public DockSide side() {
        return side;
    }

    public float scale() {
        return scale;
    }

    /** Rows actually shown (1..{@link SlabTestKit#ROWS}). */
    public int rows() {
        return Math.max(1, Math.min(SlabTestKit.ROWS, rows));
    }

    /** Item id string at grid index {@code i} (0..44), or {@code minecraft:air} when out of range. */
    public String itemIdAt(int i) {
        if (i < 0 || i >= itemIds.size()) {
            return "minecraft:air";
        }
        return itemIds.get(i);
    }

    /**
     * Resolves the config id at grid index {@code i} to an {@link ItemStack}, tolerating a
     * syntactically-invalid id (e.g. a hand-edited {@code minecraft:Oak_Slab} with an illegal capital)
     * by returning {@link ItemStack#EMPTY} instead of throwing. This is the single resolution path the
     * client overlay uses, so a bad config file can never crash the palette on open. Kept here (not in
     * the render-only Screen) so a headless gametest can exercise the fallback.
     */
    public ItemStack resolveStackAt(int i) {
        Identifier id = Identifier.tryParse(itemIdAt(i));
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    // --- mutators (each persists) --------------------------------------------------------------

    public void cycleSide() {
        side = side == DockSide.LEFT ? DockSide.RIGHT : DockSide.LEFT;
        save();
    }

    private static final float[] SCALE_STEPS = {0.75f, 1.0f, 1.5f};

    public void cycleScale() {
        int idx = 0;
        for (int i = 0; i < SCALE_STEPS.length; i++) {
            if (Math.abs(SCALE_STEPS[i] - scale) < 1.0e-4f) {
                idx = i;
                break;
            }
        }
        scale = SCALE_STEPS[(idx + 1) % SCALE_STEPS.length];
        save();
    }

    public void cycleRows() {
        rows = rows() >= SlabTestKit.ROWS ? 1 : rows() + 1;
        save();
    }

    /** Sets the item at grid index {@code i} to {@code id} and persists (options edit-mode). */
    public void setItemAt(int i, Identifier id) {
        while (itemIds.size() < SlabTestKit.SIZE) {
            itemIds.add("minecraft:air");
        }
        if (i >= 0 && i < itemIds.size()) {
            itemIds.set(i, id.toString());
            save();
        }
    }

    // --- persistence ---------------------------------------------------------------------------

    /** Serializes to a JSON string (kept separate from disk I/O so it round-trips in tests). */
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("side", side.name());
        root.addProperty("scale", scale);
        root.addProperty("rows", rows);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (String id : itemIds) {
            arr.add(id);
        }
        root.add("items", arr);
        return GSON.toJson(root);
    }

    /** Parses a config from a JSON string, filling any missing field with its default. */
    public static SlabPaletteConfig fromJson(String json) {
        SlabPaletteConfig config = new SlabPaletteConfig();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("side")) {
            try {
                config.side = DockSide.valueOf(root.get("side").getAsString());
            } catch (IllegalArgumentException ignored) {
                // keep default
            }
        }
        if (root.has("scale")) {
            config.scale = root.get("scale").getAsFloat();
        }
        if (root.has("rows")) {
            config.rows = root.get("rows").getAsInt();
        }
        if (root.has("items") && root.get("items").isJsonArray()) {
            config.itemIds.clear();
            for (com.google.gson.JsonElement e : root.getAsJsonArray("items")) {
                config.itemIds.add(e.getAsString());
            }
        }
        // Always keep exactly SIZE cells so positional addressing stays valid.
        while (config.itemIds.size() < SlabTestKit.SIZE) {
            config.itemIds.add("minecraft:air");
        }
        return config;
    }

    private static SlabPaletteConfig read() {
        return readFrom(FILE);
    }

    /**
     * Disk read for an explicit path — the real one delegates here, and a gametest can exercise the
     * corrupt-file recovery on a temp file. A corrupt/unreadable file is first backed up to
     * {@code <name>.bak} (overwriting any older backup) so the maintainer's hand edits are never silently lost,
     * then defaults are written in its place. This runs in client-init context, so it stays SILENT —
     * no chat, no logging — it just preserves and recovers.
     */
    public static SlabPaletteConfig readFrom(Path file) {
        if (Files.exists(file)) {
            try {
                return fromJson(Files.readString(file));
            } catch (IOException | RuntimeException e) {
                backupCorrupt(file);
            }
        }
        SlabPaletteConfig defaults = new SlabPaletteConfig();
        defaults.saveTo(file);
        return defaults;
    }

    private static void backupCorrupt(Path file) {
        try {
            Path bak = file.resolveSibling(file.getFileName().toString() + ".bak");
            Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Best-effort preservation; if the copy fails we still recover to defaults below.
        }
    }

    private void save() {
        saveTo(FILE);
    }

    private void saveTo(Path file) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, toJson());
        } catch (IOException e) {
            // Best-effort; a failed write just means settings don't persist this session.
        }
    }
}
