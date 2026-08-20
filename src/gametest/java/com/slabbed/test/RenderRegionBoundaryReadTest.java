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
 * THE CRASH: on the chunk-meshing thread the view is a bounds-limited {@code RenderSectionRegion}
 * that THROWS on a read outside its border (older render regions clamped to air instead). The
 * resolver does wide column walks and adjacent-column side-support reads that reach past that
 * border for a block near the region edge, so meshing ordinary terrain at a region boundary could
 * kill the client — routinely, on Terrain-slab-dense terrain, on world load.
 *
 * <p>The former remedy caught the throw at the model's outer entry and returned dy {@code 0.0} for
 * the WHOLE block, so a block near a region border rendered flush rather than at its real height.
 * The read is now bounded in one accessor instead, so only the out-of-bounds read ends and the rest
 * of the resolution finishes.
 *
 * <p><b>What these rows do and do not prove.</b> They drive the real resolver through a view that
 * throws exactly like a render region does, which is the mechanism. They do NOT prove anything
 * about a rendered frame — no gametest can. They also exercise the live walk rather than the frozen
 * store, because the gametest JVM pins {@code slabbed.frozenDy=false}; under the shipped default the
 * public read returns the stored fact without walking, and it is the other resolver entry points the
 * render path calls that walk.
 *
 * <p><b>The teeth are in {@link #outsideARegionAnOutOfBoundsReadStillThrows}.</b> Answering air
 * everywhere would make the first row pass while silently swallowing genuine defects, so the pair
 * must be read together.
 */
public final class RenderRegionBoundaryReadTest {

    /** Sentinel type the detector recognises, standing in for the client-only region class. */
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
                // Exactly how the real region fails: an array index off the end of its copied section.
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

    private static BoundedThrowingView boundedViewAround(GameTestHelper helper, BlockPos absolute) {
        // radius 0: every neighbour probe the resolver makes lands outside and throws, which is the
        // worst case a real region border can present.
        return new BoundedThrowingView(helper.getLevel(), absolute, 0);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void insideARegionAnOutOfBoundsReadEndsAsAir(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.STONE_SLAB);
        BlockPos absolute = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(absolute);

        SlabSupport.registerChunkRendererRegionDetector(view -> view instanceof BoundedThrowingView);
        try {
            // Must not propagate. The value itself is not the assertion — surviving the walk is.
            double dy = SlabSupport.getYOffset(boundedViewAround(helper, absolute), absolute, state);
            if (!Double.isFinite(dy)) {
                throw helper.assertionException(
                        "a bounded region read must resolve to a finite height, got: " + dy);
            }
        } catch (IndexOutOfBoundsException crashed) {
            StringBuilder where = new StringBuilder();
            StackTraceElement[] frames = crashed.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 8); i++) {
                where.append(" | ").append(frames[i]);
            }
            throw helper.assertionException(
                    "a read past a render-region border must end as air, not propagate: " + crashed
                            + where);
        } finally {
            SlabSupport.registerChunkRendererRegionDetector(ignored -> false);
        }
        helper.succeed();
    }

    /**
     * The stone-slab row above proves ONE lane. Resolution branches hard by block family — connector
     * arms, standing objects, thin top layers and ceiling hangers each walk different code and reach
     * different classes — and the first version of this fix guarded only {@code SlabSupport}, leaving
     * {@code SlabAnchorAttachment} to walk straight through the hole. One lane passing is therefore
     * not evidence the others are covered; each family below is its own subject.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void everyResolutionLaneSurvivesABoundedRegion(GameTestHelper helper) {
        net.minecraft.world.level.block.Block[] lanes = {
                Blocks.STONE_SLAB,        // slab lane
                Blocks.OAK_FENCE,         // connector-arm lane
                Blocks.TORCH,             // standing-object lane
                // 26.x moved dyed blocks behind ColorCollection, so Blocks.CARPET is not a Block.
                // MOSS_CARPET is a plain CarpetBlock and exercises the same thin-top-layer lane.
                Blocks.MOSS_CARPET,       // thin-top-layer lane
                Blocks.LANTERN,           // ceiling-hanger lane
                Blocks.OAK_TRAPDOOR,      // sub-cell top-face lane
                Blocks.STONE,             // plain full cube
        };

        SlabSupport.registerChunkRendererRegionDetector(view -> view instanceof BoundedThrowingView);
        try {
            for (net.minecraft.world.level.block.Block lane : lanes) {
                BlockPos relative = new BlockPos(1, 1, 1);
                helper.setBlock(relative, lane);
                BlockPos absolute = helper.absolutePos(relative);
                BlockState state = helper.getLevel().getBlockState(absolute);
                try {
                    SlabSupport.getYOffset(boundedViewAround(helper, absolute), absolute, state);
                } catch (IndexOutOfBoundsException escaped) {
                    StackTraceElement[] frames = escaped.getStackTrace();
                    String site = frames.length > 1 ? frames[1].toString() : "unknown";
                    throw helper.assertionException(
                            "the " + lane.getName().getString() + " lane walked past the region border "
                                    + "and propagated — an unguarded read remains at: " + site);
                }
            }
        } finally {
            SlabSupport.registerChunkRendererRegionDetector(ignored -> false);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void outsideARegionAnOutOfBoundsReadStillThrows(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.STONE_SLAB);
        BlockPos absolute = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(absolute);

        // Detector says "not a region", so the guard must decline to absorb the throw. Swallowing it
        // here would hide real defects behind a bounds check meant only for the meshing view.
        SlabSupport.registerChunkRendererRegionDetector(ignored -> false);
        boolean threw = false;
        try {
            SlabSupport.getYOffset(boundedViewAround(helper, absolute), absolute, state);
        } catch (IndexOutOfBoundsException expected) {
            threw = true;
        }
        if (!threw) {
            throw helper.assertionException(
                    "outside a render region an out-of-bounds read must still throw — the guard is "
                            + "absorbing failures it was never meant to see");
        }
        helper.succeed();
    }
}
