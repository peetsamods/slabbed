package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedServerHitValidation;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Focused proof of the server hit-tolerance CENTER-SHIFT SEAM:
 * {@link SlabbedServerHitValidation#shiftedValidationCenter} is the pure decision the
 * {@code ServerInteractBlockHitToleranceMixin} redirect feeds into vanilla's own
 * per-axis {@code 1.0000001} tolerance test inside
 * {@code ServerPlayNetworkHandler.onPlayerInteractBlock}.
 *
 * <p>Covered here: flush owner (vanilla center unchanged), a {@code -0.5} anchored
 * owner (center shifted {@code -0.5}), and a {@code -1.0} compound owner (center
 * shifted {@code -1.0}). These tests prove the HELPER seam only — the full
 * end-to-end packet-path proof (real client use packet through
 * {@code onPlayerInteractBlock}) is the maintainer's live matrix, deliberately NOT faked
 * here.
 */
public final class ServerHitToleranceShiftTest {

    private static final double EPS = 1.0e-6;

    private static void assertCenter(TestContext ctx, Vec3d actual, Vec3d expected, String what) {
        ctx.assertTrue(
                Math.abs(actual.x - expected.x) <= EPS
                        && Math.abs(actual.y - expected.y) <= EPS
                        && Math.abs(actual.z - expected.z) <= EPS,
                what + ": expected validation center " + expected + ", got " + actual);
    }

    /** Flush full block: dy = 0.0, the validation center must be vanilla's ofCenter, untouched. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flushOwnerKeepsVanillaValidationCenter(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos stone = ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 2, 5);
        w.setBlockState(stone, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(w, stone, w.getBlockState(stone));
        ctx.assertTrue(Math.abs(dy) <= EPS, "fixture: free-standing stone must be flush, got " + dy);

        assertCenter(ctx,
                SlabbedServerHitValidation.shiftedValidationCenter(w, stone),
                Vec3d.ofCenter(stone),
                "flush owner");
        ctx.complete();
    }

    /**
     * Anchored {@code -0.5} owner (same anchored-support scene idiom as
     * {@link SlabOnSlabVerticalAnchorTest}: dirt resting on a vanilla BOTTOM slab,
     * anchored at placement): the validation center must shift down by exactly the
     * owner's dy.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredHalfLoweredOwnerShiftsCenterByOwnDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos bottomSlab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos dirt = bottomSlab.up();

        w.setBlockState(bottomSlab,
                Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirt, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));

        double dy = SlabSupport.getYOffset(w, dirt, w.getBlockState(dirt));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "fixture: anchored dirt on a bottom slab must render -0.5, got " + dy);

        assertCenter(ctx,
                SlabbedServerHitValidation.shiftedValidationCenter(w, dirt),
                Vec3d.ofCenter(dirt).add(0.0, -0.5, 0.0),
                "-0.5 anchored owner");
        ctx.complete();
    }

    /**
     * Compound {@code -1.0} owner (same mixed-slab scene the STRICT lane of
     * {@link CombinedSlabChainingMatrixTest} hard-asserts: full block capping a
     * vanilla BOTTOM slab on a terrain BOTTOM slab): the validation center must
     * shift down by the full compound dy — this is exactly the owner whose legal
     * lower-band hit sits up to 1.5 below the vanilla center and was silently
     * rejected.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void compoundFullyLoweredOwnerShiftsCenterByOwnDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 2, 2);
        BlockPos mixedSlab = base.up();
        BlockPos cap = mixedSlab.up();

        Block terrainSlab = Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
        ctx.assertTrue(terrainSlab != Blocks.AIR,
                "fixture: terrainslabs:grass_slab must be registered under runGameTest");

        w.setBlockState(base, terrainSlab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(mixedSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(cap, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, cap, w.getBlockState(cap));

        double dy = SlabSupport.getYOffset(w, cap, w.getBlockState(cap));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "fixture: full block on a mixed slab must compound to -1.0, got " + dy);

        assertCenter(ctx,
                SlabbedServerHitValidation.shiftedValidationCenter(w, cap),
                Vec3d.ofCenter(cap).add(0.0, -1.0, 0.0),
                "-1.0 compound owner");
        ctx.complete();
    }
}
