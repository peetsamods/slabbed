package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/** Regression coverage for GitHub #35's renderer-region bounds handling. */
public final class RendererRegionBoundsGuardTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recognizedPartialRendererViewStopsAtItsBoundsWithoutBroadlySwallowingErrors(TestContext ctx) {
        ThrowingBlockView rendererRegion = new ThrowingBlockView("renderer-region");
        ThrowingBlockView unrelatedView = new ThrowingBlockView("unrelated-view");

        SlabSupport.registerChunkRendererRegionDetector(view -> view == rendererRegion);
        try {
            boolean found = SlabSupport.hasLoweringSourceInColumnBelow(rendererRegion, BlockPos.ORIGIN);
            ctx.assertTrue(!found,
                    "an out-of-region renderer lookup must terminate the bounded column scan");

            boolean unrelatedRethrown = false;
            try {
                SlabSupport.hasLoweringSourceInColumnBelow(unrelatedView, BlockPos.ORIGIN);
            } catch (ArrayIndexOutOfBoundsException expected) {
                unrelatedRethrown = expected.getMessage().contains("unrelated-view");
            }
            ctx.assertTrue(unrelatedRethrown,
                    "the guard must not swallow IndexOutOfBoundsException from unrelated BlockView implementations");
            ctx.complete();
        } finally {
            // This test is registered only in the dedicated-server GameTest source set. Restore the
            // no-client default so later server tests cannot inherit the identity-based fixture.
            SlabSupport.registerChunkRendererRegionDetector(view -> false);
        }
    }

    private record ThrowingBlockView(String label) implements BlockView {
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            throw new ArrayIndexOutOfBoundsException(label + ": synthetic renderer bound");
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getBottomY() {
            return -64;
        }
    }
}
