package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deterministic, Mojang-mapped proof for the "lowered slab/block pass-through
 * ghost" on the 26.1.2 port.
 *
 * <p><b>Mechanism under test.</b> MC's movement broadphase
 * ({@code BlockCollisions} / {@code CollisionGetter.getBlockCollisions}) is
 * cell-bounded: it assumes a block's collision shape lives inside its own unit
 * cube. For a block whose vanilla collision is the default
 * ({@code hasCollision ? state.getShape(world,pos) : empty}), the
 * collision shape is produced by {@code getShape} — the SAME method Slabbed's
 * {@code slabbed$offsetOutline} mixin lowers by {@code dy} for the visual
 * outline. So in 26.1.2 the outline offset BLEEDS into movement collision via
 * vanilla delegation: a lowered block's collision shape hangs to
 * {@code min y = dy} (e.g. -0.5), into the cell below, where the broadphase
 * never samples it → the player walks through (the ghost).
 *
 * <p>This is independent of the removed {@code getCollisionShape} injection
 * (that only ever touched fence/wall/grindstone).
 *
 * <p><b>How the ghost is prevented NOW (updated 2026-08-28).</b> The original fix kept movement
 * collision vanilla — within its own cell — by suppressing the outline offset during a collision
 * query. That is no longer how the guarantee is delivered: {@code BlockCollisionsLoweredAboveMixin}
 * unions a lowered block's hanging part into the cell below when the broadphase queries it, so the
 * drawn volume is occupied regardless of which cell owns the shape. Collision therefore follows the
 * visual (GH #31 — otherwise a phantom half-block sat above every lowered surface).
 *
 * <p><b>Assertions.</b> Outcome, not mechanism, so either implementation may satisfy them: the block
 * is solid in the part of its DRAWN volume that hangs below its own cell (the band where the original
 * ghost was felt), and a player-sized box standing on its cell collides. The old
 * {@code min Y >= 0} shape assertion was removed because it pinned the first implementation and
 * would reject any fix that made collision follow the visual.
 *
 * <p>Does NOT mutate production logic; pure measurement/regression.
 */
public final class GhostLoweredCollisionProofTest {

    private static final double EPS = 1.0e-6;

    /**
     * Full block above a bottom slab: visual lowers to -0.5, but physical
     * movement collision must stay vanilla (within-cell) so the broadphase
     * samples it.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredFullBlockCollisionStaysWithinCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos slabRel = new BlockPos(2, 2, 2);
        BlockPos blockRel = slabRel.above();

        helper.setBlock(slabRel,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        helper.setBlock(blockRel, Blocks.STONE.defaultBlockState());

        BlockPos blockAbs = helper.absolutePos(blockRel);
        BlockState blockState = level.getBlockState(blockAbs);

        // Setup sanity: this configuration must actually lower the full block.
        double dy = SlabSupport.getYOffset(level, blockAbs, blockState);
        if (dy >= -EPS) {
            throw helper.assertionException(blockRel,
                    "SETUP: stone-above-bottom-slab did not lower (dy=" + dy
                    + "); cannot test the collision bleed");
        }

        // CORE: the block must be SOLID THROUGHOUT ITS DRAWN VOLUME, including the part that hangs
        // into the cell below. This is the anti-ghost invariant itself.
        //
        // It replaced a mechanism pin (2026-08-28). That pin asserted the collision shape's
        // minY >= 0 — "stays within its own cell" — which was the ORIGINAL fix's implementation, from
        // before BlockCollisionsLoweredAboveMixin existed: with no hanging union, a shape reaching
        // below its cell was simply dropped by the cell-bounded broadphase and the player fell through.
        // The union now supplies that lower part when the broadphase queries the cell below, so
        // "within its own cell" is no longer what keeps a player out — being solid where drawn is.
        // Pinning the implementation also actively blocked the GH #31 fix, because collision could not
        // follow the visual without tripping it, which left a phantom half-block above every lowered
        // surface. Asserting the outcome instead admits either implementation and rejects both defects.
        //
        // This is STRICTLY STRONGER than the pin it replaced: minY >= 0 says nothing about whether the
        // drawn volume is actually occupied, and the sub-cell band below is exactly where the original
        // ghost was felt.
        double drawnMinY = blockAbs.getY()
                + blockState.getShape(level, blockAbs, CollisionContext.empty()).bounds().minY;
        AABB inHangingPart = new AABB(
                blockAbs.getX() + 0.3, drawnMinY + 0.05, blockAbs.getZ() + 0.3,
                blockAbs.getX() + 0.7, drawnMinY + 0.45, blockAbs.getZ() + 0.7);
        if (level.noCollision(inHangingPart)) {
            throw helper.assertionException(blockRel,
                    "GHOST: lowered full block (dy=" + dy + ") is NOT solid in the part of its drawn"
                    + " volume that hangs below its own cell (drawn minY=" + drawnMinY + "). The"
                    + " broadphase is dropping the hanging shape, so the player passes through the"
                    + " lower half of what they can see.");
        }

        // CORROBORATION: a player-sized box standing on the block's cell must collide.
        AABB playerBox = new AABB(
                blockAbs.getX() + 0.2, blockAbs.getY() + 0.01, blockAbs.getZ() + 0.2,
                blockAbs.getX() + 0.8, blockAbs.getY() + 1.81, blockAbs.getZ() + 0.8);
        boolean noCollision = level.noCollision(playerBox);
        if (noCollision) {
            throw helper.assertionException(blockRel,
                    "GHOST: broadphase yielded NO collision for a player box on the lowered"
                    + " block's cell (drawn minY=" + drawnMinY + ", dy=" + dy + ") — pass-through.");
        }

        helper.succeed();
    }

    /**
     * Pure-vanilla control: a full block on solid ground (no slab) has dy=0 and
     * a within-cell collision shape. Guards against the fix accidentally
     * disturbing un-lowered blocks.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaFullBlockCollisionUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos groundRel = new BlockPos(2, 2, 2);
        BlockPos blockRel = groundRel.above();
        helper.setBlock(groundRel, Blocks.STONE.defaultBlockState());
        helper.setBlock(blockRel, Blocks.STONE.defaultBlockState());

        BlockPos blockAbs = helper.absolutePos(blockRel);
        BlockState blockState = level.getBlockState(blockAbs);

        double dy = SlabSupport.getYOffset(level, blockAbs, blockState);
        if (Math.abs(dy) > EPS) {
            throw helper.assertionException(blockRel,
                    "CONTROL: full block on solid ground should have dy=0, got " + dy);
        }
        VoxelShape collision = blockState.getCollisionShape(level, blockAbs, CollisionContext.empty());
        double minY = collision.isEmpty() ? 0.0 : collision.min(Direction.Axis.Y);
        if (Math.abs(minY) > EPS) {
            throw helper.assertionException(blockRel,
                    "CONTROL: vanilla full block collision minY should be 0, got " + minY);
        }
        helper.succeed();
    }
}
