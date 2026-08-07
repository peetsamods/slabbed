package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.dev.SlabdyRowFormatter;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * The /slabdy debug-overlay tooling suite: headless proof of {@link SlabdyRowFormatter}'s row
 * output (re-derived from Forge 1.20.1's TargetDyOverlay.targetLines), including two regressions
 * the tool itself shipped — the outline double-offset (the overlay applied dy a second time on an
 * already-mixin-shifted shape, making the diagnostic lie) and the cache-vs-fresh diagnostic line
 * (proving the cache MISS path renders real values). The live wiring (crosshair read, HUD print)
 * is client-only and live-only; this suite proves the field computation a tester reads.
 *
 * <p>Merged 2026-08-07 from {@code SlabdyRowFormatterTest}, {@code SlabdyCacheVsFreshDiagnosticTest},
 * {@code SlabdyOutlineDoubleOffsetTest} — every test preserved verbatim.
 */
public final class SlabdyToolingSuite {

    // ── row formatter fields (SlabdyRowFormatterTest) ────────────────────────────────────────

    private static String join(List<String> lines) {
        return String.join(" || ", lines);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rowReportsFlushBlockAsZeroDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        List<String> lines = SlabdyRowFormatter.formatRow(
                w, pos, w.getBlockState(pos),
                Direction.UP, new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), ItemStack.EMPTY,
                "missing", null);
        String row = join(lines);

        ctx.assertTrue(lines.size() == 8, "row must have exactly 8 lines (7 + the cache-vs-fresh diagnostic): " + row);
        ctx.assertTrue(row.contains("dy=0.000 flush"), "flat stone must report dy=0.000 flush: " + row);
        ctx.assertTrue(row.contains("src=-"), "a non-lowered, non-anchored block must report src=-: " + row);
        ctx.assertTrue(row.contains("held=empty"), "an empty held item must report held=empty: " + row);
        ctx.assertTrue(row.contains("modelTraceArmed=-"), "no trace armed when the caller passes null: " + row);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rowReportsUpperAndLowerHalfCorrectly(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        // A full-cube outline spans [pos.y, pos.y+1]; midpoint is pos.y+0.5.
        Vec3d upperHit = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5);
        Vec3d lowerHit = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);

        String upperRow = join(SlabdyRowFormatter.formatRow(
                w, pos, w.getBlockState(pos), Direction.UP, upperHit, ItemStack.EMPTY, "missing", null));
        String lowerRow = join(SlabdyRowFormatter.formatRow(
                w, pos, w.getBlockState(pos), Direction.UP, lowerHit, ItemStack.EMPTY, "missing", null));

        ctx.assertTrue(upperRow.contains("half=UPPER"),
                "a hit point near the top of a flush full cube must report half=UPPER: " + upperRow);
        ctx.assertTrue(lowerRow.contains("half=LOWER"),
                "a hit point near the bottom of a flush full cube must report half=LOWER: " + lowerRow);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rowReportsFenceLoweredOnVanillaSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slabPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fencePos = slabPos.up();
        w.setBlockState(fencePos, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        List<String> lines = SlabdyRowFormatter.formatRow(
                w, fencePos, w.getBlockState(fencePos), Direction.NORTH, new Vec3d(3.5, 2.6, 3.0),
                new ItemStack(Blocks.OAK_FENCE.asItem()), "missing", fencePos);
        String row = join(lines);

        ctx.assertTrue(row.contains("dy=-0.500 LOWERED"),
                "a fence lowered onto a vanilla bottom slab must report dy=-0.500 LOWERED: " + row);
        ctx.assertTrue(row.contains("src=geometric"),
                "a lowered, non-anchored block falls back to src=geometric: " + row);
        ctx.assertTrue(row.contains("face=north"), "the reported face must match what was passed in: " + row);
        ctx.assertTrue(row.contains("held=minecraft:oak_fence"), "the held item must be reported by id: " + row);
        ctx.assertTrue(row.contains("below="), "a below= line must be present: " + row);
        ctx.assertTrue(row.contains("modelTraceArmed=" + fencePos.toShortString()),
                "the echoed armed position must match what the caller passed in: " + row);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rowReportsBelowBlockContext(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slabPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fencePos = slabPos.up();
        w.setBlockState(fencePos, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        List<String> lines = SlabdyRowFormatter.formatRow(
                w, fencePos, w.getBlockState(fencePos), Direction.NORTH, new Vec3d(3.5, 2.6, 3.0),
                ItemStack.EMPTY, "missing", null);
        String belowLine = lines.get(6);

        ctx.assertTrue(belowLine.startsWith("  below=" + slabPos.toShortString()),
                "the below= line must report the slab position directly under the fence: " + belowLine);
        ctx.assertTrue(belowLine.contains("minecraft:oak_slab"),
                "the below= line must report the slab's block id: " + belowLine);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rowHandlesMissingHitVecAndFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        List<String> lines = SlabdyRowFormatter.formatRow(
                w, pos, w.getBlockState(pos), null, null, null, "missing", null);
        String row = join(lines);

        ctx.assertTrue(row.contains("hit=none"), "a null hit vector must degrade to hit=none, not crash: " + row);
        ctx.assertTrue(row.contains("local=none"), "a null hit vector must degrade local=none too: " + row);
        ctx.assertTrue(row.contains("held=empty"), "a null held item must degrade to held=empty, not crash: " + row);
        ctx.assertTrue(row.contains("face=none"), "a null face must degrade to face=none, not crash: " + row);
        ctx.assertTrue(row.contains("expectedPlace=none"), "a null face means no expectedPlace: " + row);
        ctx.complete();
    }

    // ── cache-vs-fresh diagnostic line (SlabdyCacheVsFreshDiagnosticTest) ────────────────────
    // The "cache is genuinely stale" branch cannot be exercised headlessly (the cache is only
    // written by getVisualYOffset's real-ClientWorld branch); this proves the MISS path renders
    // real values, so the line is provably wired, not placeholder text.

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cacheLineShowsMissAndMatchingFreshValueOnAServerWorld(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirtPos = slabPos.up();

        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));

        ctx.assertTrue(SlabSupport.peekCachedClientVisualYOffset(dirtPos) == null,
                "setup: a fresh gametest ServerWorld must never have populated the client cache");

        List<String> lines = SlabdyRowFormatter.formatRow(w, dirtPos, w.getBlockState(dirtPos),
                Direction.UP, new Vec3d(dirtPos.getX() + 0.5, dirtPos.getY() + 0.5, dirtPos.getZ() + 0.5),
                ItemStack.EMPTY, "missing", null);

        String cacheLine = lines.stream().filter(l -> l.startsWith("  cache:")).findFirst().orElse("");
        ctx.assertTrue(cacheLine.contains("target=" + dirtPos.toShortString() + "(minecraft:dirt) cache=MISS fresh=-0.500"),
                "cache line must show MISS for an unpopulated cache and the correct fresh value; got: " + cacheLine);
        ctx.assertTrue(!cacheLine.contains("STALE"),
                "a cache MISS must never be reported as STALE (nothing to compare against); got: " + cacheLine);
        ctx.complete();
    }

    // ── outline double-offset regression (SlabdyOutlineDoubleOffsetTest) ─────────────────────
    // Live-reported 2026-07-04: overlay showed dy=-0.500 but a -1.0 outline shift — the tool
    // applied dy on top of SlabSupportStateMixin's already-shifted getOutlineShape. The real
    // render path was unaffected; only the diagnostic lied.

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void overlayOutlineMatchesSingleDyShiftNotDouble(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirtPos = slabPos.up();

        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos), "setup: dirt must anchor on the bottom slab");

        List<String> lines = SlabdyRowFormatter.formatRow(w, dirtPos, w.getBlockState(dirtPos),
                Direction.UP, new Vec3d(dirtPos.getX() + 0.5, dirtPos.getY() + 0.5, dirtPos.getZ() + 0.5),
                ItemStack.EMPTY, "missing", null);

        String outlineLine = lines.stream().filter(l -> l.contains("outlineMinY=")).findFirst().orElse("");
        ctx.assertTrue(outlineLine.contains("outlineMinY=-0.500"),
                "THE FIX: an anchored dirt at dy=-0.500 must show outlineMinY=-0.500 (a SINGLE "
                        + "offset, matching the native full-cube shape [0,0,0]->[1,1,1] shifted "
                        + "once) -- not -1.000 (a double-applied offset). Got: " + outlineLine);
        ctx.complete();
    }
}
