package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * THE INVARIANT: the resolver must NEVER absorb a render-region bounds escape into a plausible
 * answer. It lets the escape propagate, and the render entry point declines the whole resolution to
 * flush — what vanilla draws.
 *
 * <p><b>Why absorbing is worse than crashing.</b> Air is POSITIVE evidence here, not a neutral
 * absence. {@code isCantileverFullBlockCandidate} sinks a full block precisely BECAUSE nothing is
 * below it, so a read that answers air when it merely could not see makes a block resting on solid
 * ground sink. The adjacent-side lanes treat an air neighbour as "keep looking", turning a true
 * {@code -1.0} into {@code -0.5}. Both are wrong heights with no crash, no log and no marker — the
 * player sees a build quietly deform, and nothing anywhere reports a fault.
 *
 * <p>A per-read {@code getBlockStateOrAir} accessor was added on 2026-08-20 and reverted the same
 * day for exactly this reason. These rows exist so it cannot come back unnoticed.
 *
 * <p><b>Two lanes, and they must be read together.</b> A row asserting only "the escape propagates"
 * would pass even if the resolver had become unable to answer anything; a row asserting only
 * "ordinary reads work" would pass with substitution restored. The pair pins the boundary.
 */
public final class RenderRegionBoundaryReadTest {

    /** A view bounded like a chunk-render region: inside its box it answers truthfully, outside it throws. */
    private static final class BoundedThrowingView implements BlockGetter {
        private final BlockGetter delegate;
        private final BlockPos center;
        private final int radius;

        BoundedThrowingView(BlockGetter delegate, BlockPos center, int radius) {
            this.delegate = delegate;
            this.center = center;
            this.radius = radius;
        }

        private boolean outside(BlockPos pos) {
            return Math.abs(pos.getX() - center.getX()) > radius
                    || Math.abs(pos.getY() - center.getY()) > radius
                    || Math.abs(pos.getZ() - center.getZ()) > radius;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (outside(pos)) {
                // Exactly how a real region fails: an index off the end of its copied sections.
                throw new ArrayIndexOutOfBoundsException("outside the bounded test region: " + pos);
            }
            return delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return outside(pos) ? Blocks.AIR.defaultBlockState().getFluidState() : delegate.getFluidState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return outside(pos) ? null : delegate.getBlockEntity(pos);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinY() {
            return delegate.getMinY();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aBoundsEscapePropagatesInsteadOfBeingAbsorbed(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.STONE_SLAB);
        BlockPos absolute = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(absolute);

        // radius 0: every neighbour probe lands outside, the worst case a real border presents.
        BlockGetter bounded = new BoundedThrowingView(helper.getLevel(), absolute, 0);

        boolean propagated = false;
        try {
            SlabSupport.getYOffset(bounded, absolute, state);
        } catch (IndexOutOfBoundsException expected) {
            propagated = true;
        }
        if (!propagated) {
            throw helper.assertionException(
                    "the resolver absorbed a bounds escape and returned a height anyway. Air is positive "
                            + "evidence here — substituting it makes a grounded block sink. Let the escape "
                            + "reach the render entry point, which declines to flush.");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRegionThatCanSeeItsEvidenceAnswersTheSameAsTheLevel(GameTestHelper helper) {
        // The other half. A bound that broke ordinary resolution would pass the row above and be
        // useless; this pins that a view which CAN see everything answers exactly what the level does.
        BlockPos slabRel = new BlockPos(1, 1, 1);
        BlockPos objRel = new BlockPos(1, 2, 1);
        helper.setBlock(slabRel, Blocks.STONE_SLAB);
        helper.setBlock(objRel, Blocks.STONE);
        BlockPos objAbs = helper.absolutePos(objRel);
        BlockState objState = helper.getLevel().getBlockState(objAbs);

        double fromLevel = SlabSupport.getYOffset(helper.getLevel(), objAbs, objState);
        // radius 8 comfortably contains the whole column walk.
        BlockGetter roomy = new BoundedThrowingView(helper.getLevel(), objAbs, 8);
        double fromRegion = SlabSupport.getYOffset(roomy, objAbs, objState);

        if (Math.abs(fromLevel - fromRegion) > 1.0e-9) {
            throw helper.assertionException(
                    "a region that can see its evidence must answer exactly what the level answers — level="
                            + fromLevel + " region=" + fromRegion);
        }
        helper.succeed();
    }
}
