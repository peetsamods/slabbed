package com.slabbed.test;

import com.slabbed.client.palette.SlabPaletteConfig;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless validation of the shared {@link SlabTestKit} palette (the single source of truth both the
 * client overlay and the test-kit commands read) plus a round-trip of the client
 * {@link SlabPaletteConfig} serialization.
 *
 * <p>These are pure-logic assertions — no world manipulation — but run under the gametest harness so
 * the item registry is populated.
 */
public final class SlabTestKitPaletteTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paletteIsAFullGridOfResolvableItems(GameTestHelper helper) {
        List<Identifier> ids = SlabTestKit.PALETTE;
        if (ids.size() != SlabTestKit.SIZE) {
            throw helper.assertionException(
                    "PALETTE must be exactly " + SlabTestKit.SIZE + " cells, was " + ids.size());
        }

        // Every id must resolve in the item registry.
        for (Identifier id : ids) {
            if (!SlabTestKit.isRegistered(id)) {
                throw helper.assertionException("PALETTE id does not resolve in the item registry: " + id);
            }
        }

        // At least 36 distinct non-air items (a full hotbar's worth, per the acceptance criteria).
        Identifier air = Identifier.fromNamespaceAndPath("minecraft", "air");
        Set<Identifier> distinctNonAir = new HashSet<>();
        for (Identifier id : ids) {
            if (!id.equals(air)) {
                distinctNonAir.add(id);
            }
        }
        if (distinctNonAir.size() < 36) {
            throw helper.assertionException(
                    "PALETTE must have >= 36 distinct non-air items, had " + distinctNonAir.size());
        }

        // resolve() must be positionally aligned with PALETTE.
        List<Item> items = SlabTestKit.resolve();
        if (items.size() != ids.size()) {
            throw helper.assertionException("resolve() size " + items.size() + " != PALETTE size " + ids.size());
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void configRoundTripsAndAppliesMutations(GameTestHelper helper) {
        // Defaults from an empty document: side RIGHT, scale 1.0, rows 5, items = test-kit.
        SlabPaletteConfig defaults = SlabPaletteConfig.fromJson("{}");
        if (defaults.side() != SlabPaletteConfig.DockSide.RIGHT) {
            throw helper.assertionException("default dock side should be RIGHT, was " + defaults.side());
        }
        if (Math.abs(defaults.scale() - 1.0f) > 1.0e-4f) {
            throw helper.assertionException("default scale should be 1.0, was " + defaults.scale());
        }
        if (defaults.rows() != SlabTestKit.ROWS) {
            throw helper.assertionException("default rows should be " + SlabTestKit.ROWS + ", was " + defaults.rows());
        }
        if (!defaults.itemIdAt(0).equals(SlabTestKit.PALETTE.get(0).toString())) {
            throw helper.assertionException("default cell 0 should be the first test-kit item");
        }

        // Idempotent serialization round-trip.
        String json = defaults.toJson();
        SlabPaletteConfig reparsed = SlabPaletteConfig.fromJson(json);
        if (!reparsed.toJson().equals(json)) {
            throw helper.assertionException("config did not round-trip byte-identically through JSON");
        }

        // Explicit mutations parse back.
        SlabPaletteConfig mutated = SlabPaletteConfig.fromJson(
                "{\"side\":\"LEFT\",\"scale\":1.5,\"rows\":3,\"items\":[\"minecraft:stone\"]}");
        if (mutated.side() != SlabPaletteConfig.DockSide.LEFT) {
            throw helper.assertionException("mutated side should be LEFT");
        }
        if (Math.abs(mutated.scale() - 1.5f) > 1.0e-4f) {
            throw helper.assertionException("mutated scale should be 1.5");
        }
        if (mutated.rows() != 3) {
            throw helper.assertionException("mutated rows should be 3");
        }
        if (!mutated.itemIdAt(0).equals("minecraft:stone")) {
            throw helper.assertionException("mutated cell 0 should be minecraft:stone");
        }
        // A short items list must be padded to a full grid so positional reads stay valid.
        if (!mutated.itemIdAt(SlabTestKit.SIZE - 1).equals("minecraft:air")) {
            throw helper.assertionException("short items list should pad trailing cells with air");
        }
        helper.succeed();
    }

    /**
     * A syntactically-invalid config id (a hand edit with an illegal capital, e.g.
     * {@code minecraft:Oak_Slab}) must NOT crash resolution — {@link SlabPaletteConfig#resolveStackAt}
     * returns {@link ItemStack#EMPTY} for that cell while valid neighbours still resolve. This is the
     * headless proof of the tryParse fallback that keeps the palette open from crashing the client.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void invalidConfigIdResolvesToEmptyNotCrash(GameTestHelper helper) {
        // Cell 0 = illegal id (capital letters are not valid in an Identifier path), cell 1 = valid.
        SlabPaletteConfig config = SlabPaletteConfig.fromJson(
                "{\"items\":[\"minecraft:Oak_Slab\",\"minecraft:stone\"]}");

        ItemStack bad = config.resolveStackAt(0);
        if (!bad.isEmpty()) {
            throw helper.assertionException(
                    "an invalid config id must resolve to EMPTY, got " + bad);
        }
        ItemStack good = config.resolveStackAt(1);
        if (good.isEmpty()) {
            throw helper.assertionException("a valid neighbour id (minecraft:stone) must still resolve");
        }
        // Padding cells (air) also resolve to EMPTY without throwing.
        if (!config.resolveStackAt(SlabTestKit.SIZE - 1).isEmpty()) {
            throw helper.assertionException("trailing air padding should resolve to EMPTY");
        }
        helper.succeed();
    }

    /**
     * Corrupt JSON on disk must recover to defaults WITHOUT losing the user's file: the original bytes
     * are preserved to a {@code .bak}, and the main file is rewritten to valid JSON.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void corruptFileIsBackedUpThenRewrittenToDefaults(GameTestHelper helper) {
        try {
            Path dir = Files.createTempDirectory("slabbed-palette-test");
            Path file = dir.resolve("slabbed-palette.json");
            Path bak = dir.resolve("slabbed-palette.json.bak");

            // Valid object followed by trailing garbage — Gson rejects the unconsumed tail.
            String corrupt = "{\"side\":\"LEFT\",\"scale\":1.5}  <<< not json >>>";
            byte[] original = corrupt.getBytes(StandardCharsets.UTF_8);
            Files.write(file, original);

            SlabPaletteConfig recovered = SlabPaletteConfig.readFrom(file);

            // (1) recovers to defaults (RIGHT / 1.0 / test-kit), NOT the corrupt file's LEFT/1.5.
            if (recovered.side() != SlabPaletteConfig.DockSide.RIGHT) {
                throw helper.assertionException("corrupt file should recover to default side RIGHT, was "
                        + recovered.side());
            }
            if (Math.abs(recovered.scale() - 1.0f) > 1.0e-4f) {
                throw helper.assertionException("corrupt file should recover to default scale 1.0, was "
                        + recovered.scale());
            }
            if (!recovered.itemIdAt(0).equals(SlabTestKit.PALETTE.get(0).toString())) {
                throw helper.assertionException("recovered cell 0 should be the first test-kit item");
            }

            // (2) the .bak preserves the ORIGINAL corrupt bytes exactly.
            if (!Files.exists(bak)) {
                throw helper.assertionException("corrupt file should have been backed up to .bak");
            }
            if (!Arrays.equals(Files.readAllBytes(bak), original)) {
                throw helper.assertionException("the .bak must hold the original corrupt bytes verbatim");
            }

            // (3) the main file was rewritten to VALID JSON (re-reads cleanly, no second backup churn).
            String rewritten = Files.readString(file);
            SlabPaletteConfig reread = SlabPaletteConfig.fromJson(rewritten);
            if (reread.side() != SlabPaletteConfig.DockSide.RIGHT) {
                throw helper.assertionException("rewritten main file should parse as valid default config");
            }
        } catch (IOException e) {
            throw helper.assertionException("temp-file I/O failed: " + e.getMessage());
        }
        helper.succeed();
    }
}
