package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.test.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Slabbed 1.21.1 dy fingerprint.
 *
 * <p>Each row pins one historically important behavior and emits a flat log line:
 * {@code SLABBED-FP | name | dy=... | src=...}. Diff those lines across versions
 * instead of comparing behavior from memory.
 */
public final class Slabbed1211DyFingerprintTest {
    private static final double EPS = 1.0e-6;

    private static BlockState slab(SlabType type) {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static void author(TestContext ctx, BlockPos rel, BlockState state) {
        ServerWorld world = ctx.getWorld();
        BlockPos abs = ctx.getAbsolutePos(rel);
        world.setBlockState(abs, state, Block.NOTIFY_LISTENERS);
        state.getBlock().onPlaced(world, abs, world.getBlockState(abs), null, null);
    }

    private static String src(ServerWorld world, BlockPos abs, BlockState state) {
        if (SlabAnchorAttachment.isFrozenFlat(world, abs)) {
            return "FROZEN-FLAT";
        }
        if (!(state.getBlock() instanceof SlabBlock) && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, abs)) {
            return "compound-anchor";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, abs, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, abs, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, abs, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, abs, state)) {
            return "compound-side";
        }
        if (SlabAnchorAttachment.isAnchored(world, abs)) {
            return "ANCHORED";
        }
        return "geometric";
    }

    private static void fingerprint(TestContext ctx, BlockPos rel, String name, double expected) {
        ServerWorld world = ctx.getWorld();
        BlockPos abs = ctx.getAbsolutePos(rel);
        BlockState state = world.getBlockState(abs);
        double dy = SlabSupport.getYOffset(world, abs, state);
        Slabbed.LOGGER.info("SLABBED-FP | {} | dy={} | src={}",
                name, String.format(java.util.Locale.ROOT, "%.3f", dy), src(world, abs, state));
        ctx.assertTrue(Math.abs(dy - expected) <= EPS,
                "FINGERPRINT DRIFT [" + name + "]: expected dy=" + expected + " got " + dy
                        + ". If intentional, update dy-baseline.txt with the test assertion.");
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpPlainLower(TestContext ctx) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ctx.setBlockState(pos.down(), slab(SlabType.BOTTOM));
        ctx.setBlockState(pos, Blocks.STONE);
        fingerprint(ctx, pos, "plain_lower", -0.5);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpTopSlabFlush(TestContext ctx) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ctx.setBlockState(pos.down(), slab(SlabType.TOP));
        ctx.setBlockState(pos, Blocks.STONE);
        fingerprint(ctx, pos, "top_slab_flush", 0.0);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpDoubleSlabFlush(TestContext ctx) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ctx.setBlockState(pos.down(), slab(SlabType.DOUBLE));
        ctx.setBlockState(pos, Blocks.STONE);
        fingerprint(ctx, pos, "double_slab_flush", 0.0);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpCompoundVertical(TestContext ctx) {
        BlockPos base = new BlockPos(2, 1, 2);
        ctx.setBlockState(base, Blocks.STONE);
        ctx.setBlockState(base.up(), slab(SlabType.BOTTOM));
        ctx.setBlockState(base.up(2), Blocks.STONE);
        ctx.setBlockState(base.up(3), slab(SlabType.BOTTOM));
        ctx.setBlockState(base.up(4), Blocks.STONE);
        fingerprint(ctx, base.up(4), "compound_vertical", -1.0);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpFrozenFlat(TestContext ctx) {
        BlockPos pos = new BlockPos(2, 3, 2);
        author(ctx, pos, Blocks.STONE.getDefaultState());
        ctx.setBlockState(pos.down(), slab(SlabType.BOTTOM));
        fingerprint(ctx, pos, "frozen_flat", 0.0);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpFreezeControl(TestContext ctx) {
        BlockPos pos = new BlockPos(2, 3, 2);
        ctx.setBlockState(pos.down(), slab(SlabType.BOTTOM));
        ctx.setBlockState(pos, Blocks.STONE);
        fingerprint(ctx, pos, "freeze_control", -0.5);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpSideSlabMerge(TestContext ctx) {
        BlockPos slab = new BlockPos(2, 2, 2);
        BlockPos loweredFull = slab.up();
        BlockPos sideSlab = loweredFull.east();
        ctx.setBlockState(slab, slab(SlabType.BOTTOM));
        ctx.setBlockState(loweredFull, Blocks.STONE);
        ctx.setBlockState(sideSlab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        fingerprint(ctx, sideSlab, "side_slab_merge", -0.5);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpCeilingFlush(TestContext ctx) {
        BlockPos roots = new BlockPos(2, 3, 2);
        ctx.setBlockState(roots.up(), slab(SlabType.BOTTOM));
        ctx.setBlockState(roots, Blocks.HANGING_ROOTS);
        ctx.setBlockState(roots.down(), Blocks.STONE);
        ctx.setBlockState(roots.down(2), slab(SlabType.BOTTOM));
        fingerprint(ctx, roots, "ceiling_flush", -0.5);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpCeilingFollow(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = new BlockPos(2, 3, 2);
        ctx.setBlockState(support.down(), slab(SlabType.BOTTOM));
        ctx.setBlockState(support, Blocks.STONE);
        BlockPos supportAbs = ctx.getAbsolutePos(support);
        SlabAnchorAttachment.addAnchor(world, supportAbs, world.getBlockState(supportAbs));
        ctx.setBlockState(support.down(), Blocks.AIR);
        BlockPos roots = support.down();
        ctx.setBlockState(roots, Blocks.HANGING_ROOTS);
        fingerprint(ctx, roots, "ceiling_follow", -0.5);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpLanternSmoosh(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = new BlockPos(2, 3, 2);
        ctx.setBlockState(support.down(), slab(SlabType.BOTTOM));
        ctx.setBlockState(support, Blocks.STONE);
        BlockPos supportAbs = ctx.getAbsolutePos(support);
        SlabAnchorAttachment.addAnchor(world, supportAbs, world.getBlockState(supportAbs));
        ctx.setBlockState(support.down(), Blocks.AIR);
        BlockPos lantern = support.down();
        ctx.setBlockState(lantern, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        fingerprint(ctx, lantern, "lantern_smoosh", -0.5);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpThinLayerCarpet(TestContext ctx) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ctx.setBlockState(pos.down(), slab(SlabType.BOTTOM));
        ctx.setBlockState(pos, Blocks.WHITE_CARPET);
        fingerprint(ctx, pos, "thin_layer_carpet", 0.0);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpPowderSnow(TestContext ctx) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ctx.setBlockState(pos.down(), slab(SlabType.BOTTOM));
        ctx.setBlockState(pos, Blocks.POWDER_SNOW);
        fingerprint(ctx, pos, "powder_snow", 0.0);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fpBedEitherHalf(TestContext ctx) {
        BlockPos foot = new BlockPos(2, 2, 2);
        BlockPos head = foot.east();
        BlockState footState = Blocks.RED_BED.getDefaultState()
                .with(Properties.BED_PART, BedPart.FOOT)
                .with(Properties.HORIZONTAL_FACING, Direction.EAST);
        BlockState headState = Blocks.RED_BED.getDefaultState()
                .with(Properties.BED_PART, BedPart.HEAD)
                .with(Properties.HORIZONTAL_FACING, Direction.EAST);
        ctx.setBlockState(foot.down(), slab(SlabType.BOTTOM));
        ctx.setBlockState(head.down(), Blocks.STONE);
        ctx.setBlockState(foot, footState);
        ctx.setBlockState(head, headState);
        fingerprint(ctx, foot, "bed_foot_on_slab", -0.5);
        fingerprint(ctx, head, "bed_head_follows_foot", -0.5);
        ctx.complete();
    }
}
