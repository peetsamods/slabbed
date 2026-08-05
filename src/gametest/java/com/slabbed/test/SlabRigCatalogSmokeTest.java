package com.slabbed.test;

import com.slabbed.command.SlabRigCommand;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Smoke coverage for the {@code /slabrig} scene rig (donor intent: {@code SlabRigCommandSmokeTest} +
 * {@code SlabRigCaseCatalogTest}), Stage 1 catalog and Stage 2 {@code mega} / {@code rows}.
 *
 * <p>Pins the properties the live workflow depends on:
 * <ol>
 *   <li>every catalogued case BUILDS (all planned cells written) and CLEARS (all of them removed),
 *       with every cell inside the 8x8x8 plot — the rig is useless if a case half-lands;
 *   <li>the rig refuses to overwrite anything it did not place, in both directions: it will not
 *       build into an occupied footprint, and {@code clear} leaves player-edited cells alone;
 *   <li>the four LIVE_LEDGER symptom cases are present by name — those are the scenes Maintainer
 *       re-tests after the -1.0 boundary fix, so losing one silently would be a real regression;
 *   <li>{@code mega} / {@code rows} build, clear, and — the Stage 2 proof — SELF-VERIFY: every
 *       support variant's reference marker measures the dy that variant's sign claims;
 *   <li>the kit census per support variant, so "the rig places almost nothing" can never silently
 *       come back.
 * </ol>
 *
 * <p>All scenario cells stay within structure-relative 0..7. {@code mega} is exercised with two kit
 * columns, which is what the plot fits; the 40-column board is a live-world affordance.
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

    // -------------------------------------------------------------------------
    // Stage 2 — /slabrig mega and /slabrig rows
    // -------------------------------------------------------------------------

    /**
     * {@code mega} builds and clears like every other rig. Two kit columns is what fits inside the
     * 8x8x8 plot (sign column 0, reference column 2, kit columns 4 and 6, plus row 3's overhang arm
     * at x 7); the full 40-column board is a live-world affordance, not a plot-sized one.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void megaBuildsAndClears(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        SlabRigCommand.BuiltRig built = SlabRigCommand.buildMega(world, origin, 2);
        ctx.assertTrue(built != null, "mega must build at a clear origin");

        for (Map.Entry<BlockPos, BlockState> cell : built.plan().cells().entrySet()) {
            BlockPos rel = cell.getKey().subtract(origin);
            ctx.assertTrue(inPlot(rel),
                    "mega plans a cell outside the 8x8x8 plot: relative " + rel.toShortString());
            ctx.assertTrue(world.getBlockState(cell.getKey()).equals(cell.getValue()),
                    "mega did not land at " + rel.toShortString() + ": expected " + cell.getValue()
                            + ", found " + world.getBlockState(cell.getKey()));
        }

        // One tally row per support variant, and every variant actually carried the kit columns.
        ctx.assertTrue(built.report().rows().size() == 4,
                "mega must report one tally row per support variant, got "
                        + built.report().rows().size());

        int cells = built.plan().size();
        ctx.assertTrue(SlabRigCommand.buildMega(world, origin, 2) == null,
                "mega must refuse to build into its own occupied footprint");

        SlabRigCommand.ClearReport report = SlabRigCommand.clear(world, built.plan());
        ctx.assertTrue(report.removed() == cells && report.keptForeign() == 0,
                "mega clear removed " + report.removed() + "/" + cells
                        + " cells (kept " + report.keptForeign() + ")");
        for (BlockPos pos : built.plan().cells().keySet()) {
            ctx.assertTrue(world.getBlockState(pos).isAir(),
                    "mega left " + pos.subtract(origin).toShortString() + " behind after clear: "
                            + world.getBlockState(pos));
        }
        ctx.complete();
    }

    /**
     * THE Stage 2 proof: the rig's own self-verification. Each of the four support variants carries
     * a {@code stripped_jungle_log} reference marker, and the marker must MEASURE the dy the row's
     * sign claims ({@code 0.0 / -0.5 / -1.0 / -0.5}).
     *
     * <p>A mismatch here is a real regression in the support-dy resolver, NOT a fixture to relax —
     * the -1.0 row in particular is the LIVE_LEDGER boundary that {@code 6a7cd43a} closed.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void megaRowsMeasureTheirDeclaredDy(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        SlabRigCommand.BuiltRig built = SlabRigCommand.buildMega(world, origin, 0);
        ctx.assertTrue(built != null, "mega must build at a clear origin");

        List<SlabRigCommand.DyCheck> checks = built.plan().checks();
        ctx.assertTrue(checks.size() == 4,
                "mega must self-verify one reference marker per support variant, got " + checks.size());

        List<String> mismatches = SlabRigCommand.verify(world, built.plan());
        ctx.assertTrue(mismatches.isEmpty(),
                "mega does not measure what its signs say: " + String.join("; ", mismatches));

        SlabRigCommand.clear(world, built.plan());
        ctx.complete();
    }

    /** {@code rows} builds its two bare seat rows, self-verifies both, and clears. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rowsBuildsAndMeasuresBothSeats(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        SlabRigCommand.BuiltRig built = SlabRigCommand.buildRows(world, origin, 2);
        ctx.assertTrue(built != null, "rows must build at a clear origin");

        for (BlockPos pos : built.plan().cells().keySet()) {
            BlockPos rel = pos.subtract(origin);
            ctx.assertTrue(inPlot(rel),
                    "rows plans a cell outside the 8x8x8 plot: relative " + rel.toShortString());
        }
        ctx.assertTrue(built.plan().checks().size() == 2,
                "rows must self-verify one marker per row, got " + built.plan().checks().size());

        List<String> mismatches = SlabRigCommand.verify(world, built.plan());
        ctx.assertTrue(mismatches.isEmpty(),
                "rows does not measure what its signs say: " + String.join("; ", mismatches));

        int cells = built.plan().size();
        SlabRigCommand.ClearReport report = SlabRigCommand.clear(world, built.plan());
        ctx.assertTrue(report.removed() == cells && report.keptForeign() == 0,
                "rows clear removed " + report.removed() + "/" + cells);
        ctx.complete();
    }

    /**
     * Census of the whole test kit against all four support variants: exactly which objects the rig
     * can seat on each variant, and which it refuses. Maintainer's original complaint was that the rig
     * placed almost nothing, so this pins the coverage instead of leaving it to a live eyeball.
     *
     * <p>Runs against {@code mega}'s own reference column, one item at a time, so the whole 40-item
     * kit is censused inside a single plot-sized footprint.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void megaKitCensusPerSupportVariant(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        SlabRigCommand.BuiltRig built = SlabRigCommand.buildMega(world, origin, 0);
        ctx.assertTrue(built != null, "mega must build at a clear origin");

        List<Item> kit = SlabTestKit.placeableItems();
        StringBuilder census = new StringBuilder();
        for (SlabRigCommand.DyCheck check : built.plan().checks()) {
            BlockPos subject = check.pos();
            BlockState marker = world.getBlockState(subject);
            boolean aboveWasAir = world.getBlockState(subject.up()).isAir();
            List<String> refused = new ArrayList<>();
            int placed = 0;

            world.setBlockState(subject, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            for (Item item : kit) {
                String id = Registries.ITEM.getId(item).getPath();
                BlockState state = SlabRigCommand.kitSubjectState(item);
                if (state == null) {
                    refused.add(id + "(not-a-single-cell-block)");
                    continue;
                }
                boolean twoTall = state.getBlock() instanceof DoorBlock;
                if (twoTall && !aboveWasAir) {
                    refused.add(id + "(no-headroom)");
                    continue;
                }
                if (!state.canPlaceAt(world, subject)) {
                    refused.add(id + "(canPlaceAt)");
                    continue;
                }
                placed++;
                world.setBlockState(subject, state, Block.NOTIFY_LISTENERS);
                world.setBlockState(subject, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
            world.setBlockState(subject, marker, Block.NOTIFY_LISTENERS);

            census.append('[').append(check.label()).append(" dy ").append(check.expected())
                    .append("] placed ").append(placed).append('/').append(kit.size());
            if (!refused.isEmpty()) {
                census.append(" refused ").append(refused);
            }
            census.append(' ');

            // Every variant must seat the great majority of the kit — the whole point of Stage 2.
            ctx.assertTrue(placed >= kit.size() - 5,
                    "support variant '" + check.label() + "' seats only " + placed + "/" + kit.size()
                            + " kit objects; refused " + refused);
        }

        // Pinned census: a change here is a real change in what the rig can build. See HANDOFF.md.
        ctx.assertTrue(census.toString().equals(EXPECTED_KIT_CENSUS),
                "kit census changed.\n  actual:   " + census + "\n  expected: " + EXPECTED_KIT_CENSUS);

        SlabRigCommand.clear(world, built.plan());
        ctx.complete();
    }

    /**
     * The census {@link #megaKitCensusPerSupportVariant} pins (measured 2026-08-05). Regenerate
     * DELIBERATELY when the kit or a seat recipe changes — never just to make this go green.
     *
     * <p>Reading it: {@code red_bed} is a two-cell HORIZONTAL subject whose head would land in the
     * next column, so the rig refuses it everywhere; {@code ladder} needs a wall to hang on and no
     * seat has one; {@code oak_hanging_sign} needs a ceiling, so it is refused on rows 0-2 and is
     * the one object that ONLY row 3 can seat; the two doors are two cells tall, so row 3's ceiling
     * costs them their headroom. Everything else in the kit seats on every variant.
     */
    private static final String EXPECTED_KIT_CENSUS =
            "[row 0 flush dy 0.0] placed 37/40 refused [red_bed(not-a-single-cell-block), "
                    + "oak_hanging_sign(canPlaceAt), ladder(canPlaceAt)] "
            + "[row 1 lowered slab dy -0.5] placed 37/40 refused [red_bed(not-a-single-cell-block), "
                    + "oak_hanging_sign(canPlaceAt), ladder(canPlaceAt)] "
            + "[row 2 compound column dy -1.0] placed 37/40 refused [red_bed(not-a-single-cell-block), "
                    + "oak_hanging_sign(canPlaceAt), ladder(canPlaceAt)] "
            + "[row 3 overhang_and_ceiling dy -0.5] placed 36/40 refused [oak_door(no-headroom), "
                    + "red_bed(not-a-single-cell-block), ladder(canPlaceAt), birch_door(no-headroom)] ";

    private static boolean inPlot(BlockPos rel) {
        return rel.getX() >= 0 && rel.getX() <= 7
                && rel.getY() >= 0 && rel.getY() <= 7
                && rel.getZ() >= 0 && rel.getZ() <= 7;
    }
}
