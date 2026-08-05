package com.slabbed.test;

import com.slabbed.command.SlabRigCommand;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * Smoke coverage for the Stage 1 {@code /slabrig} scene rig (donor intent:
 * {@code SlabRigCommandSmokeTest} + {@code SlabRigCaseCatalogTest}, scaled to Stage 1).
 *
 * <p>Pins the three properties the live workflow depends on:
 * <ol>
 *   <li>every catalogued case BUILDS (all planned cells written) and CLEARS (all of them removed),
 *       with every cell inside the 8x8x8 plot — the rig is useless if a case half-lands;
 *   <li>the rig refuses to overwrite anything it did not place, in both directions: it will not
 *       build into an occupied footprint, and {@code clear} leaves player-edited cells alone;
 *   <li>the four LIVE_LEDGER symptom cases are present by name — those are the scenes Maintainer
 *       re-tests after the -1.0 boundary fix, so losing one silently would be a real regression.
 * </ol>
 *
 * <p>All scenario cells stay within structure-relative 0..7: cases are built at the plot origin and
 * the widest case footprint is x 0..6 / y 0..7 / z 0.
 */
public final class SlabRigCatalogSmokeTest {

    /** Builds each case at the plot origin, verifies it landed, then clears it and verifies it is gone. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void everyCaseBuildsAndClears(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        List<String> names = SlabRigCommand.caseNames();
        ctx.assertTrue(!names.isEmpty(), "the rig catalog must not be empty");

        for (String name : names) {
            SlabRigCommand.RigPlan plan = SlabRigCommand.buildCase(world, origin, name);
            ctx.assertTrue(plan != null, "case '" + name + "' failed to build at a clear origin");

            for (Map.Entry<BlockPos, BlockState> cell : plan.cells().entrySet()) {
                BlockPos rel = cell.getKey().subtract(origin);
                ctx.assertTrue(inPlot(rel),
                        "case '" + name + "' plans a cell outside the 8x8x8 plot: relative "
                                + rel.toShortString());
                ctx.assertTrue(world.getBlockState(cell.getKey()).equals(cell.getValue()),
                        "case '" + name + "' did not land at " + rel.toShortString()
                                + ": expected " + cell.getValue()
                                + ", found " + world.getBlockState(cell.getKey()));
            }

            // A second build over the same footprint must refuse without touching the world.
            int cellsBefore = plan.size();
            ctx.assertTrue(SlabRigCommand.buildCase(world, origin, name) == null,
                    "case '" + name + "' must refuse to build into its own occupied footprint");

            SlabRigCommand.ClearReport report = SlabRigCommand.clear(world, plan);
            ctx.assertTrue(report.removed() == cellsBefore && report.keptForeign() == 0,
                    "case '" + name + "' clear removed " + report.removed() + "/" + cellsBefore
                            + " cells (kept " + report.keptForeign() + ")");
            for (BlockPos pos : plan.cells().keySet()) {
                ctx.assertTrue(world.getBlockState(pos).isAir(),
                        "case '" + name + "' left " + pos.subtract(origin).toShortString()
                                + " behind after clear: " + world.getBlockState(pos));
            }
        }

        ctx.complete();
    }

    /** {@code clear} must never remove a cell the player changed after the rig built it. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void clearLeavesPlayerEditsAlone(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        SlabRigCommand.RigPlan plan = SlabRigCommand.buildCase(world, origin, "seat_ladder");
        ctx.assertTrue(plan != null, "seat_ladder must build at a clear origin");

        // Stand in for a player edit: overwrite one rig cell with a different block.
        BlockPos edited = plan.cells().keySet().iterator().next();
        world.setBlockState(edited, Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);

        SlabRigCommand.ClearReport report = SlabRigCommand.clear(world, plan);
        ctx.assertTrue(report.keptForeign() == 1,
                "the edited cell must be reported as kept, got keptForeign=" + report.keptForeign());
        ctx.assertTrue(world.getBlockState(edited).isOf(Blocks.GOLD_BLOCK),
                "clear must not remove a cell the player changed; found "
                        + world.getBlockState(edited));
        ctx.assertTrue(report.removed() == plan.size() - 1,
                "clear must remove every OTHER rig cell, got " + report.removed()
                        + " of " + (plan.size() - 1));

        world.setBlockState(edited, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.complete();
    }

    /**
     * The four live-confirmed symptom families from {@code docs/process/LIVE_LEDGER.md} each have a
     * named case. These are the scenes the -1.0 boundary fix will be re-tested against.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void liveLedgerSymptomCasesArePresent(TestContext ctx) {
        List<String> names = SlabRigCommand.caseNames();
        for (String required : new String[] {
                "follower_on_minus_one",   // ledger #1 — the 0.5 gap
                "dodo_log_over_slab",      // ledger #2 — DODO
                "hanging_smoosh",          // ledger #3 — SMOOSH
                "lantern_in_trapdoor",     // ledger #4 — interpenetration
        }) {
            ctx.assertTrue(names.contains(required),
                    "LIVE_LEDGER symptom case '" + required + "' is missing from the rig catalog");
            ctx.assertTrue(SlabRigCommand.caseByName(required) != null,
                    "case '" + required + "' is listed but not resolvable");
        }
        ctx.complete();
    }

    /** Every non-padding test-kit id must resolve in the item registry (donor validation intent). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void testKitPaletteIdsAllRegistered(TestContext ctx) {
        ctx.assertTrue(SlabTestKit.PALETTE.size() == SlabTestKit.SIZE,
                "the palette must be padded to exactly " + SlabTestKit.SIZE + " cells, got "
                        + SlabTestKit.PALETTE.size());
        Identifier air = Identifier.of("minecraft", "air");
        for (Identifier id : SlabTestKit.PALETTE) {
            if (id.equals(air)) {
                continue;
            }
            ctx.assertTrue(SlabTestKit.isRegistered(id),
                    "test-kit id " + id + " is not registered in the item registry");
        }
        ctx.assertTrue(!SlabTestKit.placeableItems().isEmpty(),
                "the test kit must expose at least one placeable item");
        ctx.complete();
    }

    /**
     * Fixture precondition for the whole -1.0 family: the seat the ledger cases build really does
     * put its subject at dy -1.0 while the slab under it renders -0.5. If this ever stops holding,
     * {@code follower_on_minus_one} / {@code hanging_smoosh} / {@code lantern_in_trapdoor} are
     * quietly building the WRONG scene and a live pass over them proves nothing.
     *
     * <p>Deliberately asserts only the SUPPORT column, never the follower — the follower reading
     * -0.5 IS the open bug (LIVE_LEDGER #1), so pinning it here would turn red the day it is fixed.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void minusOneSeatReallyReadsMinusOne(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        SlabRigCommand.RigPlan plan = SlabRigCommand.buildCase(world, origin, "follower_on_minus_one");
        ctx.assertTrue(plan != null, "follower_on_minus_one must build at a clear origin");

        // Column x=0: y0 stone, y1 slab, y2 anchored slab, y3 the stripped_jungle_log support.
        BlockPos logPos = origin.add(0, 3, 0);
        ctx.assertTrue(world.getBlockState(logPos).isOf(Blocks.STRIPPED_JUNGLE_LOG),
                "fixture: the -1.0 support must be the stripped_jungle_log, found "
                        + world.getBlockState(logPos));
        double logDy = com.slabbed.util.SlabSupport.getYOffset(world, logPos, world.getBlockState(logPos));
        ctx.assertTrue(logDy == -1.0,
                "the rig's -1.0 seat must put its support at dy -1.0 (the LIVE_LEDGER boundary), got "
                        + logDy);

        BlockPos slabPos = origin.add(0, 2, 0);
        double slabDy = com.slabbed.util.SlabSupport.getYOffset(world, slabPos, world.getBlockState(slabPos));
        ctx.assertTrue(slabDy == -0.5,
                "the slab carrying the -1.0 support must itself render -0.5 (that asymmetry IS the "
                        + "symptom geometry), got " + slabDy);

        // Measured on this HEAD with the seat above (2026-08-05): the case genuinely reproduces
        // LIVE_LEDGER #1 headlessly — follower dy over a -1.0 support reads birch_slab -0.5
        // (the 0.5 gap), birch_fence 0.0 (a FULL 1.0 gap), lantern -1.0, oak_sign -1.0.
        // Deliberately NOT asserted: those are the open bug's values and would go red on the fix.
        SlabRigCommand.clear(world, plan);
        ctx.complete();
    }

    private static boolean inPlot(BlockPos rel) {
        return rel.getX() >= 0 && rel.getX() <= 7
                && rel.getY() >= 0 && rel.getY() <= 7
                && rel.getZ() >= 0 && rel.getZ() <= 7;
    }
}
