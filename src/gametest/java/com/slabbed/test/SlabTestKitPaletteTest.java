package com.slabbed.test;

import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless validation of the shared {@link SlabTestKit} palette — the curated, ordered item list the
 * server-side test-kit commands ({@code /slabkit}, {@code /slabrig mega}) read.
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
}
